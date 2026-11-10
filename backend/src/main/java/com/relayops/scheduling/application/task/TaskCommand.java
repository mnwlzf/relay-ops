package com.relayops.scheduling.application.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.relayops.scheduling.domain.ConcurrencyPolicy;
import com.relayops.scheduling.domain.MisfirePolicy;
import com.relayops.scheduling.domain.QuietHours;

/**
 * Validated admin input for creating or updating a task.
 *
 * @param spec schedule definition as JSON; parsed and validated by the service
 */
public record TaskCommand(
        String name,
        String taskType,
        JsonNode spec,
        JsonNode params,
        MisfirePolicy misfirePolicy,
        int maxCatchUp,
        ConcurrencyPolicy concurrencyPolicy,
        int timeoutSeconds,
        int retryMax,
        int retryBackoffSeconds,
        QuietHours quietHours,
        boolean enabled
) {

    public TaskCommand {
        misfirePolicy = misfirePolicy == null ? MisfirePolicy.FIRE_ONCE : misfirePolicy;
        concurrencyPolicy = concurrencyPolicy == null ? ConcurrencyPolicy.SKIP : concurrencyPolicy;
        maxCatchUp = maxCatchUp <= 0 ? 1 : maxCatchUp;
        timeoutSeconds = timeoutSeconds <= 0 ? 300 : timeoutSeconds;
        retryMax = Math.max(0, retryMax);
        retryBackoffSeconds = retryBackoffSeconds <= 0 ? 60 : retryBackoffSeconds;
    }
}
