package com.relayops.scheduling.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** One execution of a task. */
public record TaskRunRecord(
        Long id,
        long taskId,
        Instant plannedAt,
        TriggerSource source,
        Instant startedAt,
        Instant finishedAt,
        RunStatus status,
        Integer durationMs,
        String message,
        JsonNode detail
) {
}
