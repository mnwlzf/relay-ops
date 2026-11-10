package com.relayops.monitoring.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.monitoring.domain.UsageMath.SnapshotPoint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UsageMathTest {

    private static final Instant T0 = Instant.parse("2026-11-10T00:00:00Z");

    private static SnapshotPoint point(int hour, String used) {
        return new SnapshotPoint(1L, T0.plusSeconds(hour * 3600L),
                used == null ? null : new BigDecimal(used));
    }

    @Test
    void increaseIsCurrentMinusPrevious() {
        assertThat(UsageMath.increase(new BigDecimal("100"), new BigDecimal("130")))
                .contains(new BigDecimal("30"));
    }

    @Test
    void increaseIsEmptyWhenMissingOrCounterReset() {
        assertThat(UsageMath.increase(null, new BigDecimal("10"))).isEmpty();
        assertThat(UsageMath.increase(new BigDecimal("10"), null)).isEmpty();
        // upstream reset the counter: negative delta must not be reported as consumption
        assertThat(UsageMath.increase(new BigDecimal("500"), new BigDecimal("20"))).isEmpty();
    }

    @Test
    void consumptionUsesFirstAndLastSnapshotOfTheDay() {
        Optional<BigDecimal> consumption = UsageMath.consumption(List.of(
                point(10, "300"), point(1, "100"), point(5, "180")));

        assertThat(consumption).contains(new BigDecimal("200"));
    }

    @Test
    void consumptionNeedsAtLeastTwoUsablePoints() {
        assertThat(UsageMath.consumption(List.of(point(3, "100")))).isEmpty();
        assertThat(UsageMath.consumption(List.of())).isEmpty();
        assertThat(UsageMath.consumption(List.of(point(1, null), point(2, "100")))).isEmpty();
    }
}
