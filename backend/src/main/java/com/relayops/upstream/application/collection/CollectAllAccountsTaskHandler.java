package com.relayops.upstream.application.collection;

import com.relayops.scheduling.contract.ScheduledTaskHandler;
import com.relayops.scheduling.contract.TaskDescriptor;
import com.relayops.scheduling.contract.TaskExecution;
import com.relayops.scheduling.domain.ScheduleKind;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Scheduled full collection; the technical cadence comes from the task's CRON spec. */
@Component
public class CollectAllAccountsTaskHandler implements ScheduledTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(CollectAllAccountsTaskHandler.class);

    private final CollectionService collectionService;

    public CollectAllAccountsTaskHandler(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @Override
    public String taskType() {
        return "upstream.collect-all";
    }

    @Override
    public TaskDescriptor describe() {
        return new TaskDescriptor(
                "全量账号采集",
                "并发采集所有启用账号的余额与用量；不跟随工作日历，按 CRON/间隔执行",
                Set.copyOf(EnumSet.of(ScheduleKind.CRON, ScheduleKind.INTERVAL)),
                null);
    }

    @Override
    public void execute(TaskExecution execution) {
        CollectionService.Summary summary = collectionService.collectAll("SCHEDULE");
        log.info("定时采集结束: task={}, total={}, succeeded={}, failed={}",
                execution.taskName(), summary.total(), summary.succeeded(), summary.failed());
    }
}
