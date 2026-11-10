package com.relayops.scheduling.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.relayops.scheduling.domain.TriggerSource;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Read-only context handed to a {@link ScheduledTaskHandler}.
 *
 * <p>Handlers must not write back to the scheduling tables: run status is owned by the executor.</p>
 *
 * @param taskId    identifier of {@code scheduling_task}
 * @param taskName  configured name, for logging only
 * @param taskType  handler code
 * @param plannedAt the instant this occurrence was planned for (idempotency key)
 * @param params    configured params, never {@code null} (empty object when unset)
 * @param source    why this execution happened
 * @param now       current instant from the business clock
 * @param zone      task time zone
 */
public record TaskExecution(
        long taskId,
        String taskName,
        String taskType,
        Instant plannedAt,
        JsonNode params,
        TriggerSource source,
        Instant now,
        ZoneId zone
) {
}
