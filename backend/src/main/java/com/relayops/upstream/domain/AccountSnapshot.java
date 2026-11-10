package com.relayops.upstream.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One account collection result.
 *
 * @param raw upstream payload kept verbatim so parsing bugs can be replayed without re-collecting
 */
public record AccountSnapshot(
        Long id,
        long accountId,
        Instant collectedAt,
        CollectStatus status,
        BigDecimal balance,
        BigDecimal usedQuota,
        Long requestCount,
        String raw,
        String message
) {

    public static AccountSnapshot failed(long accountId, Instant at, String message) {
        return new AccountSnapshot(null, accountId, at, CollectStatus.FAILED, null, null, null, null, message);
    }

    public static AccountSnapshot skipped(long accountId, Instant at, String message) {
        return new AccountSnapshot(null, accountId, at, CollectStatus.SKIPPED, null, null, null, null, message);
    }

    /** Whether the numeric fields are good enough to drive dashboards. */
    public boolean usable() {
        return status == CollectStatus.SUCCESS || status == CollectStatus.PARTIAL;
    }
}
