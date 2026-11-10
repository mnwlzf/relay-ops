package com.relayops.monitoring.contract;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Cross-module read contract for monitoring summaries.
 *
 * <p>Other modules (alerts, copilot tools) read platform state through this interface instead of the
 * monitoring tables, which keeps the SQL coupling inside monitoring.</p>
 */
public interface MonitoringQueryPort {

    Optional<UsageSummary> latestUsage(long accountId);

    /** Latest snapshot per enabled account, ordered by collection time desc. */
    List<AccountState> accounts();

    /** @param status stored as text so callers do not depend on upstream enums */
    record AccountState(
            long accountId,
            String accountName,
            String platformCode,
            BigDecimal balance,
            BigDecimal usedQuota,
            Long requestCount,
            String status,
            Instant collectedAt
    ) {
    }

    record UsageSummary(
            long accountId,
            String balance,
            String usage,
            Instant collectedAt
    ) {
    }
}
