package com.relayops.assistant.application.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ScopeType;
import com.relayops.assistant.application.memory.ConversationService;
import com.relayops.assistant.domain.message.Blueprint;
import com.relayops.scheduling.contract.ScheduledTaskHandler;
import com.relayops.scheduling.contract.TaskDescriptor;
import com.relayops.scheduling.contract.TaskExecution;
import com.relayops.scheduling.domain.ScheduleKind;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Scheduled broadcast: composes a blueprint and hands the text to the sender.
 *
 * <p>Params: {@code blueprintCode} (or {@code blueprintId}), {@code scopeType}, {@code scopeKey},
 * {@code args} (object handed to the segments) and {@code remember} (append to conversation memory,
 * default true).</p>
 */
@Component
public class BroadcastTaskHandler implements ScheduledTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(BroadcastTaskHandler.class);

    private final BlueprintService blueprints;
    private final MessageComposer composer;
    private final MessageSender sender;
    private final ConversationService conversations;

    public BroadcastTaskHandler(BlueprintService blueprints, MessageComposer composer,
                                MessageSender sender, ConversationService conversations) {
        this.blueprints = blueprints;
        this.composer = composer;
        this.sender = sender;
        this.conversations = conversations;
    }

    @Override
    public String taskType() {
        return "assistant.broadcast";
    }

    @Override
    public TaskDescriptor describe() {
        return new TaskDescriptor(
                "定时播报",
                "按蓝图编排消息并投递；params: blueprintCode / scopeType / scopeKey / args / remember",
                Set.copyOf(EnumSet.of(ScheduleKind.CRON, ScheduleKind.INTERVAL,
                        ScheduleKind.CALENDAR_TIMES, ScheduleKind.ONCE)),
                "{\"type\":\"object\",\"properties\":{\"blueprintCode\":{\"type\":\"string\"},"
                        + "\"scopeType\":{\"type\":\"string\"},\"scopeKey\":{\"type\":\"string\"},"
                        + "\"args\":{\"type\":\"object\"},\"remember\":{\"type\":\"boolean\"}}}");
    }

    @Override
    public void execute(TaskExecution execution) {
        JsonNode params = execution.params() == null ? JsonNodeFactory.instance.objectNode() : execution.params();
        Blueprint blueprint = resolveBlueprint(params);
        ScopeRef scope = resolveScope(params);
        JsonNode args = params.path("args");

        MessageComposer.Composed composed = composer.compose(blueprint, scope, args);
        if (composed.text().isBlank()) {
            log.warn("播报内容为空，跳过投递: task={}, blueprint={}", execution.taskName(), blueprint.code());
            return;
        }
        sender.send(new MessageSender.OutboundMessage(scope, blueprint.code(), composed.text()));

        boolean remember = !params.has("remember") || params.get("remember").asBoolean(true);
        if (remember) {
            long conversationId = conversations.ensure(scope, blueprint.packId(), blueprint.personaId(), null);
            conversations.appendBroadcast(conversationId, composed.text());
        }
        log.info("定时播报完成: task={}, blueprint={}, chars={}, degraded={}",
                execution.taskName(), blueprint.code(), composed.text().length(), composed.degraded());
    }

    private Blueprint resolveBlueprint(JsonNode params) {
        if (params.hasNonNull("blueprintId")) {
            return blueprints.get(params.get("blueprintId").asLong());
        }
        String code = params.path("blueprintCode").asText(null);
        if (code == null || code.isBlank()) {
            throw new com.relayops.shared.error.BusinessException(
                    "BROADCAST_INVALID", "params 需要 blueprintCode 或 blueprintId");
        }
        return blueprints.byCode(code);
    }

    private ScopeRef resolveScope(JsonNode params) {
        String type = params.path("scopeType").asText("GLOBAL");
        String key = params.path("scopeKey").asText("*");
        try {
            return new ScopeRef(ScopeType.valueOf(type.toUpperCase(Locale.ROOT)), key);
        } catch (IllegalArgumentException ex) {
            throw new com.relayops.shared.error.BusinessException(
                    "BROADCAST_INVALID", "未知的 scopeType: " + type);
        }
    }
}
