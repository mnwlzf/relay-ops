package com.relayops.scheduling.application.execution;

import com.relayops.scheduling.domain.TriggerSource;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;

/**
 * Quartz entry point.
 *
 * <p>The job carries only {@code taskId} in its JobDataMap; params are always read from the
 * database so a configuration change never depends on serialized job data.</p>
 *
 * <p>{@link DisallowConcurrentExecution} is per JobKey, and every task owns its JobKey, so it
 * enforces per-task serialization in addition to the {@code ConcurrencyPolicy} check in the
 * executor.</p>
 */
@DisallowConcurrentExecution
public class ScheduledTaskJob extends QuartzJobBean {

    private TaskExecutionService executionService;

    @Autowired
    void setExecutionService(TaskExecutionService executionService) {
        this.executionService = executionService;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        String taskId = context.getMergedJobDataMap().getString("taskId");
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalStateException("JobDataMap 缺少 taskId");
        }
        executionService.run(
                Long.parseLong(taskId),
                context.getScheduledFireTime().toInstant(),
                TriggerSource.SCHEDULE);
    }
}
