package com.relayops.scheduling.application.task;

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
 * Diagnostic handler with no business effect.
 *
 * <p>Exists so the scheduling pipeline (trigger → executor → run history) can be verified before the
 * first business handler lands. Send {@code {"fail": true}} as params to exercise the failure and
 * retry path.</p>
 */
@Component
public class SelfTestTaskHandler implements ScheduledTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(SelfTestTaskHandler.class);

    @Override
    public String taskType() {
        return "scheduling.selftest";
    }

    @Override
    public TaskDescriptor describe() {
        return new TaskDescriptor(
                "调度自检",
                "不产生任何业务副作用，只记录日志；params.fail=true 时抛错以验证失败与重试链路",
                Set.copyOf(EnumSet.allOf(ScheduleKind.class)),
                null);
    }

    @Override
    public void execute(TaskExecution execution) {
        log.info("调度自检任务执行: taskId={}, plannedAt={}, source={}, params={}",
                execution.taskId(), execution.plannedAt(), execution.source(), execution.params());
        if (execution.params().path("fail").asBoolean(false)) {
            throw new IllegalStateException("自检任务被要求失败");
        }
    }
}
