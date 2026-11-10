package com.relayops.agent.domain.prompt;

/**
 * Ordered layers of an assembled prompt.
 *
 * <p>Lower {@link #order()} means higher priority: earlier layers survive a token budget cut, and a
 * persona can only fill the {@code PERSONA} layer. Safety and capability rules are contributed by
 * the capability pack and are not overridable.</p>
 */
public enum PromptLayer {

    /** Hard rules: no privilege escalation, no fabrication, no secret exposure. */
    SAFETY(0),

    /** Capability rules: metric definitions, tool usage, output format, redaction. */
    CAPABILITY(1),

    /** Expression preferences of the capability pack (structured report vs. casual chat). */
    PACK_STYLE(2),

    /** Persona: appearance, personality, rhythm, relationship, emotion, boundaries. */
    PERSONA(3),

    /** Recalled memory: rolling summary plus long-term facts with provenance. */
    MEMORY(4),

    /** Current session context: speaker labels, quoted message, relationship level. */
    SESSION(5);

    private final int order;

    PromptLayer(int order) {
        this.order = order;
    }

    public int order() {
        return order;
    }
}
