package com.relayops.assistant.application.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relayops.agent.application.tool.ToolRegistry;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ToolContext;
import com.relayops.agent.contract.ToolInvocation;
import com.relayops.agent.contract.ToolResult;
import com.relayops.agent.contract.ToolSpec;
import com.relayops.assistant.application.memory.TextSummarizer;
import com.relayops.assistant.domain.message.Blueprint;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.time.BusinessTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes a blueprint and produces the text to send.
 *
 * <p>Segments run in order. Tool results are addressed by their namespace for later segments, and
 * the whole run is bounded by {@code maxToolCalls}. Unresolved placeholders are left as-is so the
 * dry-run preview shows what was missing instead of silently printing an empty string.</p>
 */
@Component
public class MessageComposer {

    private static final Logger log = LoggerFactory.getLogger(MessageComposer.class);

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_TOOL_TEXT = 600;

    private final ToolRegistry toolRegistry;
    private final TextSummarizer summarizer;
    private final BusinessTime time;

    public MessageComposer(ToolRegistry toolRegistry, TextSummarizer summarizer, BusinessTime time) {
        this.toolRegistry = toolRegistry;
        this.summarizer = summarizer;
        this.time = time;
    }

    public Composed compose(Blueprint blueprint, ScopeRef scope, JsonNode params) {
        Instant now = time.now();
        ZonedDateTime local = now.atZone(ZoneId.of("Asia/Shanghai"));
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("date", DATE.format(local));
        variables.put("now", TIME.format(local));
        variables.put("yesterday", DATE.format(local.minusDays(1)));

        ToolContext context = new ToolContext(scope, "scheduler", true, true, java.util.Set.of());
        Map<String, ToolSpec> available = new LinkedHashMap<>();
        for (ToolSpec spec : toolRegistry.schedulable(scope, context)) {
            available.put(spec.id(), spec);
        }

        List<String> parts = new ArrayList<>();
        List<Outcome> outcomes = new ArrayList<>();
        Map<String, String> toolTexts = new LinkedHashMap<>();
        int toolCalls = 0;
        boolean degraded = false;

        List<Blueprint.Segment> segments = blueprint.segments();
        for (int index = 0; index < segments.size(); index++) {
            Blueprint.Segment segment = segments.get(index);
            switch (segment) {
                case Blueprint.TextSegment text -> {
                    parts.add(resolve(text.template(), variables, params, toolTexts));
                    outcomes.add(new Outcome(index, "text", "OK", ""));
                }
                case Blueprint.ToolSegment tool -> {
                    toolCalls++;
                    if (toolCalls > blueprint.options().maxToolCalls()) {
                        degraded = true;
                        outcomes.add(new Outcome(index, "tool", "SKIPPED", "超过 maxToolCalls"));
                        continue;
                    }
                    ToolSpec spec = available.get(tool.ref());
                    if (spec == null) {
                        DegradeResult result = handleFailure(tool, blueprint, parts,
                                "工具不可用于定时消息: " + tool.ref());
                        degraded |= result.degraded();
                        outcomes.add(new Outcome(index, "tool", result.status(), result.detail()));
                        continue;
                    }
                    try {
                        ToolResult result = spec.handler().invoke(new ToolInvocation(
                                tool.ref(), resolveNode(tool.args(), variables, params, toolTexts),
                                scope, "scheduler", true));
                        if (!result.success()) {
                            DegradeResult failure = handleFailure(tool, blueprint, parts, result.error());
                            degraded |= failure.degraded();
                            outcomes.add(new Outcome(index, "tool", failure.status(), failure.detail()));
                            continue;
                        }
                        String body = renderResult(result);
                        if (body == null || body.isBlank()) {
                            if (!"HIDE".equals(tool.onEmpty())) {
                                parts.add(tool.title() == null ? "（无数据）" : tool.title() + "：（无数据）");
                            }
                            outcomes.add(new Outcome(index, "tool", "EMPTY", ""));
                            continue;
                        }
                        toolTexts.put(tool.namespace(), body);
                        parts.add(tool.title() == null ? body : tool.title() + "\n" + body);
                        outcomes.add(new Outcome(index, "tool", "OK", ""));
                    } catch (Exception ex) {
                        DegradeResult failure = handleFailure(tool, blueprint, parts, ex.getMessage());
                        degraded |= failure.degraded();
                        outcomes.add(new Outcome(index, "tool", failure.status(), failure.detail()));
                    }
                }
                case Blueprint.PersonaNoteSegment note -> {
                    if (!summarizer.available()) {
                        degraded = true;
                        outcomes.add(new Outcome(index, "persona_note", "SKIPPED", "未配置模型"));
                        continue;
                    }
                    String prompt = resolve(note.prompt(), variables, params, toolTexts)
                            + "\n\n【已生成的内容】\n" + String.join("\n\n", parts);
                    Optional<String> noteText = summarizer.summarize(prompt);
                    if (noteText.isEmpty()) {
                        degraded = true;
                        outcomes.add(new Outcome(index, "persona_note", "SKIPPED", "模型未返回内容"));
                        continue;
                    }
                    parts.add(noteText.get());
                    outcomes.add(new Outcome(index, "persona_note", "OK", ""));
                }
            }
        }

        String text = String.join("\n\n", parts.stream().filter(part -> !part.isBlank()).toList());
        return new Composed(text, List.copyOf(outcomes), degraded, toolCalls);
    }

    private DegradeResult handleFailure(Blueprint.ToolSegment tool, Blueprint blueprint,
                                        List<String> parts, String detail) {
        String policy = tool.onError() == null || tool.onError().isBlank()
                ? blueprint.options().onToolError() : tool.onError();
        String message = detail == null || detail.isBlank() ? "未知错误" : detail;
        return switch (policy) {
            case "ABORT" -> throw new BusinessException("BLUEPRINT_TOOL_FAILED",
                    "工具调用失败且策略为 ABORT: " + tool.ref() + " / " + message);
            case "SKIP" -> new DegradeResult("SKIPPED", message, false);
            default -> {
                parts.add("（" + (tool.title() == null ? tool.ref() : tool.title()) + " 暂时不可用）");
                yield new DegradeResult("DEGRADED", message, true);
            }
        };
    }

    private String renderResult(ToolResult result) {
        if (result.text() != null && !result.text().isBlank()) {
            return truncate(result.text());
        }
        if (result.data() != null && !result.data().isNull()) {
            return truncate(result.data().toString());
        }
        return "";
    }

    private String truncate(String value) {
        String trimmed = value.strip();
        return trimmed.length() <= MAX_TOOL_TEXT ? trimmed : trimmed.substring(0, MAX_TOOL_TEXT) + "…";
    }

    private JsonNode resolveNode(JsonNode node, Map<String, String> variables, JsonNode params,
                                 Map<String, String> toolTexts) {
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            return com.fasterxml.jackson.databind.node.TextNode.valueOf(
                    resolve(node.asText(), variables, params, toolTexts));
        }
        if (node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).deepCopy();
            copy.fieldNames().forEachRemaining(field -> {
                JsonNode value = copy.get(field);
                if (value != null && value.isTextual()) {
                    copy.put(field, resolve(value.asText(), variables, params, toolTexts));
                }
            });
            return copy;
        }
        return node;
    }

    /** Replaces {@code {{date}}}, {@code {{args.x}}} and {@code {{tools.ns}}}; unknown keys stay. */
    String resolve(String template, Map<String, String> variables, JsonNode params,
                   Map<String, String> toolTexts) {
        if (template == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = lookup(key, variables, params, toolTexts);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement == null ? matcher.group() : replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String lookup(String key, Map<String, String> variables, JsonNode params,
                          Map<String, String> toolTexts) {
        if (variables.containsKey(key)) {
            return variables.get(key);
        }
        if (key.startsWith("args.") && params != null) {
            JsonNode value = params.path(key.substring("args.".length()));
            return value.isMissingNode() || value.isNull() ? null : value.asText();
        }
        if (key.startsWith("tools.")) {
            String rest = key.substring("tools.".length());
            int dot = rest.indexOf('.');
            String namespace = dot < 0 ? rest : rest.substring(0, dot);
            return toolTexts.get(namespace);
        }
        return null;
    }

    /** Rendered message plus per-segment outcomes for the dry-run preview. */
    public record Composed(String text, List<Outcome> outcomes, boolean degraded, int toolCalls) {
    }

    /** One segment's execution result. */
    public record Outcome(int index, String type, String status, String detail) {
    }

    private record DegradeResult(String status, String detail, boolean degraded) {
    }
}
