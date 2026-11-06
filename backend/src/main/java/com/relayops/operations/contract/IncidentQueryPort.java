package com.relayops.operations.contract;

import java.time.Instant;
import java.util.Optional;

/**
 * Cross-module read contract for the current incident state.
 */
public interface IncidentQueryPort {

    Optional<IncidentSummary> findOpenIncident(String resourceKey);

    record IncidentSummary(
            long id,
            String resourceKey,
            String severity,
            String status,
            Instant openedAt
    ) {
    }
}
