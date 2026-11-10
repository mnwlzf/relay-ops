package com.relayops.assistant.domain.memory;

/** Who authored a message. */
public enum MessageRole {

    USER,

    ASSISTANT,

    SYSTEM,

    TOOL,

    /** Content produced by a scheduled broadcast rather than a reply. */
    BROADCAST
}
