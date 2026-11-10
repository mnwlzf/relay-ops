package com.relayops.monitoring.application.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Read models of the monitoring slice. */
public final class UsageReadModel {

    private UsageReadModel() {
    }

    /**
     * One account's current standing.
     *
     * @param todayConsumption delta of {@code used_quota} since the first snapshot of today;
     *                         {@code null} when unknown (single snapshot or counter reset)
     * @param status           stored as text so this module does not depend on upstream enums
     */
    public record AccountUsage(
            long accountId,
            String accountName,
            String platformCode,
            String platformType,
            BigDecimal balance,
            BigDecimal usedQuota,
            Long requestCount,
            String status,
            Instant collectedAt,
            BigDecimal todayConsumption,
            String message
    ) {
    }

    /** One raw snapshot point for the trend drawer. */
    public record TrendPoint(Instant collectedAt, BigDecimal balance, BigDecimal usedQuota,
                             Long requestCount, String status) {
    }

    /**
     * An account worth looking at.
     *
     * @param reason FAILED / PARTIAL / STALE / NEVER_COLLECTED
     */
    public record AbnormalAccount(long accountId, String accountName, String platformCode,
                                  String reason, String message, Instant collectedAt) {
    }

    /** Raw snapshot row used by the store. */
    public record SnapshotRow(long accountId, String accountName, String platformCode, String platformType,
                              BigDecimal balance, BigDecimal usedQuota, Long requestCount,
                              String status, Instant collectedAt, String message) {
    }

    /** Aggregate for the page header. */
    public record Overview(List<AccountUsage> accounts) {

        public BigDecimal totalBalance() {
            return accounts.stream()
                    .map(AccountUsage::balance)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public java.util.Optional<BigDecimal> totalTodayConsumption() {
            return accounts.stream()
                    .map(AccountUsage::todayConsumption)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal::add);
        }
    }
}
