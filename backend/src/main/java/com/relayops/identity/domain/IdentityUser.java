package com.relayops.identity.domain;

/** Console account. */
public record IdentityUser(
        long id,
        String username,
        String passwordHash,
        String displayName,
        String role,
        boolean enabled
) {
}
