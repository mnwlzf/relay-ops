package com.relayops.scheduling.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.relayops.scheduling.domain.TaskDefinition;
import java.time.Instant;
import java.util.List;

/** Read model of a scheduled task. */
public record ScheduledTaskResponse(
        Long id,
        String name,
        String taskType,
        String kind,
        JsonNode spec,
        JsonNode params,
        String misfirePolicy,
        int maxCatchUp,
        String concurrencyPolicy,
        int timeoutSeconds,
        int retryMax,
        int retryBackoffSeconds,
        List<String> quietHours,
        boolean enabled,
        Instant nextFireAt,
        Instant lastRunAt,
        String lastRunStatus,
        String lastRunMessage,
        int version,
        Instant createdAt,
        Instant updatedAt
) {

    public static ScheduledTaskResponse of(TaskDefinition task, JsonNode specJson) {
        List<String> quiet = task.quietHours() == null
                ? List.of()
                : List.of(task.quietHours().from().toString(), task.quietHours().to().toString());
        return new ScheduledTaskResponse(
                task.id(),
                task.name(),
                task.taskType(),
                task.spec().kind().name(),
                specJson,
                task.params(),
                task.misfirePolicy().name(),
                task.maxCatchUp(),
                task.concurrencyPolicy().name(),
                task.timeoutSeconds(),
                task.retryMax(),
                task.retryBackoffSeconds(),
                quiet,
                task.enabled(),
                task.nextFireAt(),
                task.lastRunAt(),
                task.lastRunStatus(),
                task.lastRunMessage(),
                task.version(),
                task.createdAt(),
                task.updatedAt());
    }
}
