package com.relayops.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Central business clock. Inject it instead of calling Instant.now() in business code.
 */
public final class BusinessTime {

    public static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private final Clock clock;

    public BusinessTime(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public ZoneId zone() {
        return clock.getZone();
    }
}
