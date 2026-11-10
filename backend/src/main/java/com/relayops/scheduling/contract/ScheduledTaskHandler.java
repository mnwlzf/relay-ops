package com.relayops.scheduling.contract;

/**
 * SPI implemented by business modules that expose dynamically scheduled work.
 *
 * <p>Implementations are discovered by Spring and matched to {@code scheduling_task.task_type}.
 * The executor owns retries, timeouts, concurrency and run history; a handler only performs the
 * work described by its {@link TaskDescriptor}.</p>
 */
public interface ScheduledTaskHandler {

    /** Stable code stored in {@code scheduling_task.task_type}. */
    String taskType();

    /** Human readable metadata and the schedule kinds this handler accepts. */
    TaskDescriptor describe();

    /**
     * Performs the work for one occurrence.
     *
     * @throws Exception any failure; the executor records it and applies the retry policy
     */
    void execute(TaskExecution execution) throws Exception;
}
