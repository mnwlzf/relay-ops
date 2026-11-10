package com.relayops.agent.domain.persona;

/** Closeness between the bot and a user, used to pick the addressing style. */
public enum RelationshipLevel {

    /** Unknown user in a channel where the bot is not supposed to know anyone. */
    STRANGER,

    /** Regular member of a group the bot lives in. */
    GROUP_MATE,

    /** Established contact: remembers preferences across sessions. */
    FRIEND,

    /** Explicitly close relationship, only after both sides opt in. */
    CLOSE;

    public static RelationshipLevel parse(String value) {
        if (value == null || value.isBlank()) {
            return STRANGER;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return STRANGER;
        }
    }
}
