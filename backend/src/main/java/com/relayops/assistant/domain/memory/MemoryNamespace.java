package com.relayops.assistant.domain.memory;

/**
 * Retrieval namespace.
 *
 * <p>{@link #PLATFORM} facts are only visible to the platform capability pack; a general chat request
 * must never recall them, which is what keeps "访客看不到平台信息" true at the memory layer.</p>
 */
public enum MemoryNamespace {

    /** Platform business conclusions; visible only inside the platform pack. */
    PLATFORM,

    /** Everyday chat facts: preferences, shared experiences. */
    GENERAL,

    /** Safe across both, e.g. the language the user prefers. */
    SHARED
}
