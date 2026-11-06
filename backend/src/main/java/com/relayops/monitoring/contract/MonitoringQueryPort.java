package com.relayops.monitoring.contract;

import java.time.Instant;
import java.util.Optional;

/**
 * Cross-module read contract for monitoring summaries.
 */
public interface MonitoringQueryPort {

    Optional<UsageSummary> latestUsage(long accountId);

    record UsageSummary(
            long accountId,
            String balance,
            String usage,
            Instant collectedAt
    ) {
    }
}
