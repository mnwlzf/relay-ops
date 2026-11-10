package com.relayops.scheduling.domain;

/** Supported ways to describe when a task fires. */
public enum ScheduleKind {

    /** Absolute technical cadence (cron), unaffected by business calendars. */
    CRON,

    /** Fixed interval; unaffected by business calendars. */
    INTERVAL,

    /** Times of day, filtered by a day filter and a work calendar. */
    CALENDAR_TIMES,

    /** One-shot absolute instant. */
    ONCE,

    /** Times of day on the Nth (or last) workday of each month. */
    MONTH_NTH_WORKDAY
}
