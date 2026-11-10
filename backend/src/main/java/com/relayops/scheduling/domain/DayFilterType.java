package com.relayops.scheduling.domain;

/** Day-level predicate applied before the times of day are considered. */
public enum DayFilterType {

    /** Every calendar day. */
    ALL,

    /** Workdays, including 调休补班 (make-up workdays that fall on weekends). */
    WORKDAY,

    /** Holidays and ordinary weekends. */
    NON_WORKDAY,

    /** Fixed weekdays, ignoring holidays. */
    WEEKDAYS,

    /** Fixed days of the month, e.g. the 1st and the 15th. */
    MONTH_DAYS,

    /** The Nth workday of the month. */
    MONTH_NTH_WORKDAY,

    /** The last workday of the month. */
    MONTH_LAST_WORKDAY,

    /** An explicit set of dates. */
    CUSTOM_DATES
}
