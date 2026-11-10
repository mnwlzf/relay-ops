package com.relayops.scheduling.domain;

/** What to do when the calendar excludes a day the schedule wanted to fire on. */
public enum HolidayPolicy {

    /** Skip the fire; the next regular occurrence is used. */
    SKIP,

    /** Fire on the next workday instead. */
    NEXT_WORKDAY,

    /** Fire on the previous workday instead. */
    PREV_WORKDAY,

    /** Ignore the calendar and fire anyway. */
    FORCE
}
