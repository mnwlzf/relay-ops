package com.relayops.assistant.application.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relayops.assistant.domain.message.Blueprint;
import com.relayops.shared.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Parses and writes blueprint JSON, validating segment shapes at save time. */
@Component
public class BlueprintCodec {

    private final ObjectMapper objectMapper;

    public BlueprintCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Blueprint.Segment> parseSegments(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw invalid("segments 必须是数组");
        }
        List<Blueprint.Segment> segments = new ArrayList<>();
        for (JsonNode item : node) {
            String type = text(item, "type");
            if (type == null) {
                throw invalid("段缺少 type");
            }
            switch (type.toLowerCase(java.util.Locale.ROOT)) {
                case "text" -> {
                    String template = text(item, "template");
                    if (template == null) {
                        throw invalid("文本段缺少 template");
                    }
                    segments.add(new Blueprint.TextSegment(template));
                }
                case "tool" -> {
                    String ref = text(item, "ref");
                    if (ref == null) {
                        throw invalid("工具段缺少 ref");
                    }
                    segments.add(new Blueprint.ToolSegment(ref, text(item, "as"),
                            item.path("args"), text(item, "title"), text(item, "onError"), text(item, "onEmpty")));
                }
                case "persona_note" -> {
                    String prompt = text(item, "prompt");
                    if (prompt == null) {
                        throw invalid("点评段缺少 prompt");
                    }
                    segments.add(new Blueprint.PersonaNoteSegment(prompt));
                }
                default -> throw invalid("未知的段类型: " + type);
            }
        }
        return segments;
    }

    public Blueprint.Options parseOptions(JsonNode node) {
        if (node == null || node.isNull()) {
            return Blueprint.Options.defaults();
        }
        return new Blueprint.Options(
                node.has("maxToolCalls") ? node.get("maxToolCalls").asInt(8) : 8,
                text(node, "onToolError"),
                text(node, "fallback"));
    }

    public JsonNode writeSegments(List<Blueprint.Segment> segments) {
        ArrayNode array = objectMapper.createArrayNode();
        for (Blueprint.Segment segment : segments) {
            switch (segment) {
                case Blueprint.TextSegment text -> {
                    ObjectNode node = array.addObject();
                    node.put("type", "text");
                    node.put("template", text.template());
                }
                case Blueprint.ToolSegment tool -> {
                    ObjectNode node = array.addObject();
                    node.put("type", "tool");
                    node.put("ref", tool.ref());
                    if (tool.as() != null) {
                        node.put("as", tool.as());
                    }
                    node.set("args", tool.args());
                    if (tool.title() != null) {
                        node.put("title", tool.title());
                    }
                    node.put("onError", tool.onError());
                    node.put("onEmpty", tool.onEmpty());
                }
                case Blueprint.PersonaNoteSegment note -> {
                    ObjectNode node = array.addObject();
                    node.put("type", "persona_note");
                    node.put("prompt", note.prompt());
                }
            }
        }
        return array;
    }

    public JsonNode writeOptions(Blueprint.Options options) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("maxToolCalls", options.maxToolCalls());
        node.put("onToolError", options.onToolError());
        node.put("fallback", options.fallback());
        return node;
    }

    /** Rejects blueprints that reference a non-read-only or non-schedulable tool. */
    public void validate(Blueprint blueprint, java.util.function.Predicate<String> schedulableTool) {
        if (blueprint.code() == null || blueprint.code().isBlank()) {
            throw invalid("蓝图 code 不能为空");
        }
        if (blueprint.name() == null || blueprint.name().isBlank()) {
            throw invalid("蓝图名称不能为空");
        }
        if (blueprint.segments().isEmpty()) {
            throw invalid("蓝图至少需要一个段");
        }
        long toolCalls = blueprint.segments().stream()
                .filter(Blueprint.ToolSegment.class::isInstance)
                .count();
        if (toolCalls > blueprint.options().maxToolCalls()) {
            throw invalid("工具段数量超过 maxToolCalls=" + blueprint.options().maxToolCalls());
        }
        for (Blueprint.Segment segment : blueprint.segments()) {
            if (segment instanceof Blueprint.ToolSegment tool && !schedulableTool.test(tool.ref())) {
                throw invalid("工具不可用于定时消息（只读且允许调度）: " + tool.ref());
            }
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().strip();
    }

    private BusinessException invalid(String message) {
        return new BusinessException("BLUEPRINT_INVALID", message);
    }
}
