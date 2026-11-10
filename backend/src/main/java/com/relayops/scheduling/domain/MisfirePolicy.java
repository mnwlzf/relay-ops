package com.relayops.scheduling.domain;

/** Behaviour when a trigger could not fire on time (application down, long GC pause, ...). */
public enum MisfirePolicy {

    /** Drop the missed occurrence. */
    SKIP,

    /** Fire once for the missed occurrence, then continue the normal schedule. */
    FIRE_ONCE,

    /** Fire up to {@code maxCatchUp} missed occurrences (replayed by the application). */
    CATCH_UP
}
