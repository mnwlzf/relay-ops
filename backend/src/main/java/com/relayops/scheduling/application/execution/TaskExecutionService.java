package com.relayops.scheduling.application.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.relayops.scheduling.adapter.out.persistence.ScheduledTaskRepository;
import com.relayops.scheduling.adapter.out.persistence.ScheduledTaskRunRepository;
import com.relayops.scheduling.config.SchedulingProperties;
import com.relayops.scheduling.contract.ScheduledTaskHandler;
import com.relayops.scheduling.contract.TaskExecution;
import com.relayops.scheduling.domain.ConcurrencyPolicy;
import com.relayops.scheduling.domain.RunStatus;
import com.relayops.scheduling.domain.TaskDefinition;
import com.relayops.scheduling.domain.TriggerSource;
import com.relayops.scheduling.application.task.TaskRegistrationService;
import com.relayops.shared.time.BusinessTime;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Executes one occurrence of a task: idempotency, quiet hours, concurrency, timeout, history and
 * retry. Handlers stay free of all of that.
 */
@Service
public class TaskExecutionService {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionService.class);

    private final ScheduledTaskRepository tasks;
    private final ScheduledTaskRunRepository runs;
    private final ScheduledTaskHandlerRegistry handlers;
    private final TaskRegistrationService registration;
    private final ExecutorService executor;
    private final SchedulingProperties properties;
    private final BusinessTime time;

    public TaskExecutionService(ScheduledTaskRepository tasks,
                                ScheduledTaskRunRepository runs,
                                ScheduledTaskHandlerRegistry handlers,
                                TaskRegistrationService registration,
                                @org.springframework.beans.factory.annotation.Qualifier("schedulingTaskExecutor")
                                ExecutorService schedulingTaskExecutor,
                                SchedulingProperties properties,
                                BusinessTime time) {
        this.tasks = tasks;
        this.runs = runs;
        this.handlers = handlers;
        this.registration = registration;
        this.executor = schedulingTaskExecutor;
        this.properties = properties;
        this.time = time;
    }

    /** Runs one occurrence. Safe to call twice with the same {@code plannedAt}. */
    public void run(long taskId, Instant plannedAt, TriggerSource source) {
        Optional<TaskDefinition> found = tasks.find(taskId);
        if (found.isEmpty()) {
            log.warn("定时任务已被删除，忽略本次触发: taskId={}", taskId);
            return;
        }
        TaskDefinition task = found.get();
        Instant now = time.now();
        if (source == TriggerSource.SCHEDULE && !task.enabled()) {
            return;
        }
        if (source == TriggerSource.SCHEDULE && task.quietHours() != null
                && task.quietHours().contains(now.atZone(task.spec().zone()).toLocalTime())) {
            recordSkipped(task, plannedAt, source, now, "命中静默时段");
            return;
        }
        if (task.concurrencyPolicy() == ConcurrencyPolicy.SKIP && runs.existsRunning(taskId)) {
            recordSkipped(task, plannedAt, source, now, "上一次执行尚未结束");
            return;
        }

        Optional<Long> started = runs.start(taskId, plannedAt, source, now);
        if (started.isEmpty()) {
            return;
        }
        long runId = started.get();
        long startedAt = System.currentTimeMillis();
        RunStatus status;
        String message = null;
        int timeoutSeconds = task.timeoutSeconds() > 0
                ? task.timeoutSeconds()
                : (int) properties.executionTimeout().toSeconds();

        Optional<ScheduledTaskHandler> handler = handlers.find(task.taskType());
        if (handler.isEmpty()) {
            status = RunStatus.FAILED;
            message = "未注册的任务类型: " + task.taskType();
        } else {
            TaskExecution execution = new TaskExecution(taskId, task.name(), task.taskType(), plannedAt,
                    task.params(), source, now, task.spec().zone());
            Future<?> future = executor.submit(() -> {
                try {
                    handler.get().execute(execution);
                } catch (Exception ex) {
                    throw new IllegalStateException(ex);
                }
            });
            try {
                future.get(timeoutSeconds, TimeUnit.SECONDS);
                status = RunStatus.SUCCESS;
            } catch (TimeoutException ex) {
                future.cancel(true);
                status = RunStatus.TIMEOUT;
                message = "执行超时(" + timeoutSeconds + "s)";
            } catch (ExecutionException ex) {
                status = RunStatus.FAILED;
                message = rootMessage(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                status = RunStatus.FAILED;
                message = "执行被中断";
            }
        }

        long durationMs = System.currentTimeMillis() - startedAt;
        Instant finishedAt = time.now();
        runs.finish(runId, status, finishedAt, durationMs, message, (JsonNode) null);
        tasks.updateLastRun(taskId, finishedAt, status, message);

        if (status == RunStatus.SUCCESS) {
            log.info("定时任务执行成功: taskId={}, name={}, source={}, durationMs={}",
                    taskId, task.name(), source, durationMs);
        } else {
            log.warn("定时任务执行未成功: taskId={}, name={}, status={}, message={}",
                    taskId, task.name(), status, message);
            maybeRetry(task, runId, source, finishedAt);
        }
    }

    private void maybeRetry(TaskDefinition task, long runId, TriggerSource source, Instant finishedAt) {
        if (source == TriggerSource.RETRY || task.retryMax() <= 0) {
            return;
        }
        Instant at = finishedAt.plusSeconds(Math.max(1, task.retryBackoffSeconds()));
        try {
            registration.scheduleOnce(task.id(), at, TriggerSource.RETRY, "retry-" + runId);
        } catch (RuntimeException ex) {
            log.warn("登记重试失败: taskId={}", task.id(), ex);
        }
    }

    private void recordSkipped(TaskDefinition task, Instant plannedAt, TriggerSource source,
                               Instant now, String reason) {
        Optional<Long> started = runs.start(task.id(), plannedAt, source, now);
        started.ifPresent(runId -> {
            runs.finish(runId, RunStatus.SKIPPED, now, 0, reason, (JsonNode) null);
            tasks.updateLastRun(task.id(), now, RunStatus.SKIPPED, reason);
            log.info("定时任务已跳过: taskId={}, reason={}", task.id(), reason);
        });
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        if (message == null || message.isBlank()) {
            message = cursor.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
