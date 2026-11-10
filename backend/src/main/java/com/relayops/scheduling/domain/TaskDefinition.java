package com.relayops.scheduling.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;

/**
 * Fully parsed task definition. Persistence stores the raw JSON; the application works with this
 * record so that spec, quiet hours and policies are already validated.
 */
public record TaskDefinition(
        Long id,
        String taskType,
        String name,
        ScheduleSpec spec,
        JsonNode params,
        MisfirePolicy misfirePolicy,
        int maxCatchUp,
        ConcurrencyPolicy concurrencyPolicy,
        int timeoutSeconds,
        int retryMax,
        int retryBackoffSeconds,
        QuietHours quietHours,
        boolean enabled,
        Instant nextFireAt,
        Instant lastRunAt,
        String lastRunStatus,
        String lastRunMessage,
        int version,
        Instant createdAt,
        Instant updatedAt
) {

    public TaskDefinition {
        params = params == null ? JsonNodeFactory.instance.objectNode() : params;
    }

    public TaskDefinition withId(Long newId) {
        return new TaskDefinition(newId, taskType, name, spec, params, misfirePolicy, maxCatchUp,
                concurrencyPolicy, timeoutSeconds, retryMax, retryBackoffSeconds, quietHours, enabled,
                nextFireAt, lastRunAt, lastRunStatus, lastRunMessage, version, createdAt, updatedAt);
    }

    public TaskDefinition withEnabled(boolean value) {
        return new TaskDefinition(id, taskType, name, spec, params, misfirePolicy, maxCatchUp,
                concurrencyPolicy, timeoutSeconds, retryMax, retryBackoffSeconds, quietHours, value,
                nextFireAt, lastRunAt, lastRunStatus, lastRunMessage, version, createdAt, updatedAt);
    }
}
