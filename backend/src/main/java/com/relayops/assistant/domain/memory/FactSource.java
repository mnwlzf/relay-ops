package com.relayops.assistant.domain.memory;

/** Where a fact came from; drives trust and whether review is needed. */
public enum FactSource {

    /** The user said "记住…", or an operator edited it. Highest trust. */
    EXPLICIT,

    /** Written deterministically by code, e.g. after a daily report. */
    DERIVED,

    /** Extracted by the model from a conversation; may need review. */
    LLM_EXTRACT
}
