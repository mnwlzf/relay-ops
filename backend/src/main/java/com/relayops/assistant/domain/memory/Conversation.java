package com.relayops.assistant.domain.memory;

import com.relayops.agent.contract.ScopeRef;
import java.time.Instant;

/** A conversation: scope plus capability pack, with the persona version it started under. */
public record Conversation(
        Long id,
        ScopeRef scope,
        String packId,
        Long personaId,
        Integer personaVersion,
        Instant lastMessageAt,
        String status,
        Instant createdAt
) {
}
