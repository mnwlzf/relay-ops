package com.relayops.assistant.domain.memory;

import java.time.Instant;

/** One archived message of a conversation. */
public record ConversationMessage(
        Long id,
        long conversationId,
        long seq,
        MessageRole role,
        String speakerId,
        String speakerName,
        String content,
        boolean redacted,
        Instant createdAt
) {

    /** Line used when building a summary prompt. */
    public String asPromptLine() {
        String who = role == MessageRole.USER
                ? (speakerName == null || speakerName.isBlank() ? "用户" : speakerName)
                : "机器人";
        return who + "：" + content;
    }
}
