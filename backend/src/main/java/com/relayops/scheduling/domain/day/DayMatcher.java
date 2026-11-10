package com.relayops.scheduling.domain.day;

import java.time.LocalDate;

/** Decides whether the schedule is allowed to fire on a given date. */
@FunctionalInterface
public interface DayMatcher {

    boolean matches(LocalDate date, CalendarData calendar);
}
