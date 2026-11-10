package com.relayops.monitoring.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Pure calculations over collection snapshots.
 *
 * <p>{@code used_quota} is monotonic per account until the upstream resets it, so today's consumption
 * is the delta between the first and the latest snapshot of the day. A negative delta means the
 * upstream reset the counter and is reported as "unknown" instead of a bogus negative number.</p>
 */
public final class UsageMath {

    private UsageMath() {
    }

    /** {@code current - previous}; empty when either side is missing or the counter went backwards. */
    public static Optional<BigDecimal> increase(BigDecimal previous, BigDecimal current) {
        if (previous == null || current == null) {
            return Optional.empty();
        }
        BigDecimal delta = current.subtract(previous);
        return delta.signum() < 0 ? Optional.empty() : Optional.of(delta);
    }

    /** Consumption of a day: latest minus first, using the snapshot timestamps to pick the ends. */
    public static Optional<BigDecimal> consumption(List<SnapshotPoint> points) {
        if (points == null || points.size() < 2) {
            return Optional.empty();
        }
        List<SnapshotPoint> ordered = points.stream()
                .filter(point -> point.usedQuota() != null)
                .sorted(Comparator.comparing(SnapshotPoint::collectedAt))
                .toList();
        if (ordered.size() < 2) {
            return Optional.empty();
        }
        return increase(ordered.get(0).usedQuota(), ordered.get(ordered.size() - 1).usedQuota());
    }

    /** Minimal snapshot view needed for the calculations. */
    public record SnapshotPoint(long accountId, java.time.Instant collectedAt, BigDecimal usedQuota) {
    }
}
