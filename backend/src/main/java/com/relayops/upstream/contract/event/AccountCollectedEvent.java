package com.relayops.upstream.contract.event;

import java.time.Instant;

public record AccountCollectedEvent(
        long platformId,
        long accountId,
        Instant collectedAt
) {
}
