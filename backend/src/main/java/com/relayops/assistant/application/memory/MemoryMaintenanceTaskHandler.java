package com.relayops.assistant.application.memory;

import com.relayops.scheduling.contract.ScheduledTaskHandler;
import com.relayops.scheduling.contract.TaskDescriptor;
import com.relayops.scheduling.contract.TaskExecution;
import com.relayops.scheduling.domain.ScheduleKind;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Periodic memory upkeep, scheduled through the {@code scheduling} module.
 *
 * <p>Two jobs in one run: physically delete expired facts and roll up conversations that have new
 * un-summarised messages. Params: {@code minMessages} (default 10) and {@code limit} (default 5).</p>
 */
@Component
public class MemoryMaintenanceTaskHandler implements ScheduledTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(MemoryMaintenanceTaskHandler.class);

    private final MemoryService memoryService;
    private final ConversationService conversationService;

    public MemoryMaintenanceTaskHandler(MemoryService memoryService, ConversationService conversationService) {
        this.memoryService = memoryService;
        this.conversationService = conversationService;
    }

    @Override
    public String taskType() {
        return "memory.maintenance";
    }

    @Override
    public TaskDescriptor describe() {
        return new TaskDescriptor(
                "记忆维护",
                "清理过期事实并为有新增消息的会话生成滚动摘要；params 支持 minMessages 与 limit",
                Set.copyOf(EnumSet.of(ScheduleKind.CRON, ScheduleKind.INTERVAL)),
                null);
    }

    @Override
    public void execute(TaskExecution execution) {
        int minMessages = execution.params().path("minMessages").asInt(10);
        int limit = execution.params().path("limit").asInt(5);
        int purged = memoryService.purgeExpired();
        int summarized = conversationService.summarizePending(minMessages, limit);
        log.info("记忆维护完成: purged={}, summarized={}, minMessages={}", purged, summarized, minMessages);
    }
}
