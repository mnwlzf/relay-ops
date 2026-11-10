package com.relayops.agent.contract;

/** How tool output must be masked before it reaches a channel. */
public enum RedactionPolicy {

    /** Output is safe as-is. */
    NONE,

    /** Mask account names, platform names and e-mail addresses when the target is a group. */
    CHANNEL
}
