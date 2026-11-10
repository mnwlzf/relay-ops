package com.relayops.agent.contract;

/** Side effects a tool may have; only {@link #READ_ONLY} tools can be bound to scheduled messages. */
public enum SideEffect {

    /** Pure query. */
    READ_ONLY,

    /** Mutates platform state (balance, channel, key...). */
    WRITE,

    /** Irreversible or high-impact action (self-healing, deletion). */
    DANGEROUS;

    public boolean schedulable() {
        return this == READ_ONLY;
    }
}
