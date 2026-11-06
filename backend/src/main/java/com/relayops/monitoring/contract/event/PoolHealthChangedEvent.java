package com.relayops.monitoring.contract.event;

import java.time.Instant;

public record PoolHealthChangedEvent(
        String poolKey,
        HealthState previous,
        HealthState current,
        Instant occurredAt
) {

    public enum HealthState {
        HEALTHY,
        DEGRADED,
        UNHEALTHY,
        UNKNOWN
    }
}
