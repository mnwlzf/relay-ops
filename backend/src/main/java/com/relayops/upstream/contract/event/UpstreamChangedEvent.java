package com.relayops.upstream.contract.event;

import java.time.Instant;

public record UpstreamChangedEvent(
        long platformId,
        long accountId,
        ChangeType changeType,
        Instant occurredAt
) {

    public enum ChangeType {
        API_KEY,
        CHANNEL,
        GROUP,
        BALANCE
    }
}
