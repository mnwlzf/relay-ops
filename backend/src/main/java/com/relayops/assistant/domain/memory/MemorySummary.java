package com.relayops.assistant.domain.memory;

import java.time.Instant;

/** Rolling summary of a conversation, anchored at a message id so it can be rebuilt. */
public record MemorySummary(
        Long id,
        long conversationId,
        long uptoMessageId,
        String summary,
        Integer tokens,
        String model,
        Instant createdAt
) {
}
