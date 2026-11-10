package com.relayops.upstream.domain;

import java.time.Instant;

/** A configured upstream account; the credential stays encrypted until the moment of collection. */
public record UpstreamAccount(
        Long id,
        long platformId,
        String platformCode,
        PlatformType platformType,
        String baseUrl,
        String name,
        String login,
        String credentialCipher,
        String credentialHint,
        boolean enabled,
        Instant lastCollectAt,
        CollectStatus lastCollectStatus,
        String lastCollectMessage
) {
}
