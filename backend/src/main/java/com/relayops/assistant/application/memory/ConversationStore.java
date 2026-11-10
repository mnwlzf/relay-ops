package com.relayops.assistant.application.memory;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.domain.memory.Conversation;
import com.relayops.assistant.domain.memory.ConversationMessage;
import com.relayops.assistant.domain.memory.MemorySummary;
import com.relayops.assistant.domain.memory.MessageRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Storage port of L1/L2 memory (conversations, messages, summaries).
 *
 * <p>Separate from {@link MemoryStore} because the access patterns differ: facts are queried by
 * relevance, messages are appended and read by sequence.</p>
 */
public interface ConversationStore {

    /** Finds or creates the conversation of a scope and pack. */
    long ensure(ScopeRef scope, String packId, Long personaId, Integer personaVersion, Instant now);

    Optional<Conversation> find(long conversationId);

    long append(long conversationId, MessageRole role, String speakerId, String speakerName,
                String content, boolean redacted, Instant now);

    List<ConversationMessage> recent(long conversationId, int limit);

    Optional<MemorySummary> latestSummary(long conversationId);

    void saveSummary(long conversationId, long uptoMessageId, String summary, Integer tokens,
                     String model, Instant now);

    /** Conversations with new messages since their last summary, oldest activity first. */
    List<Long> needingSummary(int minMessages, int limit);

    long messageCount(long conversationId);
}
