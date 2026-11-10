package com.relayops.agent.contract;

/** Granularity of a conversation scope. */
public enum ScopeType {

    /** Application-wide default. */
    GLOBAL,

    /** A transport channel, e.g. all QQ traffic. */
    CHANNEL,

    /** A group chat. */
    GROUP,

    /** A single person. */
    USER
}
