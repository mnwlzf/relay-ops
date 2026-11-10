package com.relayops.notification.application.alert;

import com.relayops.scheduling.contract.ScheduledTaskHandler;
import com.relayops.scheduling.contract.TaskDescriptor;
import com.relayops.scheduling.contract.TaskExecution;
import com.relayops.scheduling.domain.ScheduleKind;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Scheduled alert sweep ({@code notification.alerts}). */
@Component
public class AlertTaskHandler implements ScheduledTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(AlertTaskHandler.class);

    private final AlertService alertService;

    public AlertTaskHandler(AlertService alertService) {
        this.alertService = alertService;
    }

    @Override
    public String taskType() {
        return "notification.alerts";
    }

    @Override
    public TaskDescriptor describe() {
        return new TaskDescriptor(
                "采集告警检查",
                "余额低于阈值、采集失败或超过预期未采集时发送邮件；同类告警按冷却窗口去重",
                Set.copyOf(EnumSet.of(ScheduleKind.CRON, ScheduleKind.INTERVAL)),
                null);
    }

    @Override
    public void execute(TaskExecution execution) {
        int sent = alertService.run();
        log.info("告警检查完成: task={}, alerts={}", execution.taskName(), sent);
    }
}
