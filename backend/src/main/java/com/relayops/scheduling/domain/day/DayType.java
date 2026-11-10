package com.relayops.scheduling.domain.day;

/** Classification of a single calendar day. */
public enum DayType {

    /** Ordinary weekday. */
    WORKDAY,

    /** Make-up workday: a weekend day turned into a working day by 调休. */
    MAKEUP_WORKDAY,

    /** Ordinary weekend. */
    WEEKEND,

    /** Public holiday. */
    HOLIDAY;

    public boolean isWorkday() {
        return this == WORKDAY || this == MAKEUP_WORKDAY;
    }
}
