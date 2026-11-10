package com.relayops.scheduling.domain.day;

import com.relayops.scheduling.domain.DayFilter;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

/**
 * Factory for {@link DayMatcher} implementations.
 *
 * <p>All matchers are pure functions of the date and the calendar data, which keeps preview and
 * execution consistent: the same matcher instance feeds both the Quartz calendar and the
 * "next N fire times" preview.</p>
 */
public final class DayMatchers {

    private DayMatchers() {
    }

    public static DayMatcher from(DayFilter filter) {
        if (filter == null) {
            return (date, calendar) -> true;
        }
        return switch (filter.type()) {
            case ALL -> (date, calendar) -> true;
            case WORKDAY -> (date, calendar) -> calendar.isWorkday(date);
            case NON_WORKDAY -> (date, calendar) -> !calendar.isWorkday(date);
            case WEEKDAYS -> {
                Set<java.time.DayOfWeek> weekdays = filter.weekdays();
                yield (date, calendar) -> weekdays.contains(date.getDayOfWeek());
            }
            case MONTH_DAYS -> {
                Set<Integer> days = filter.daysOfMonth();
                yield (date, calendar) -> days.contains(date.getDayOfMonth());
            }
            case CUSTOM_DATES -> {
                Set<LocalDate> dates = filter.dates();
                yield (date, calendar) -> dates.contains(date);
            }
            case MONTH_NTH_WORKDAY -> {
                int nth = filter.nth() == null ? 1 : filter.nth();
                yield (date, calendar) -> isNthWorkday(date, calendar, nth);
            }
            case MONTH_LAST_WORKDAY -> DayMatchers::isLastWorkday;
        };
    }

    /** {@code true} when {@code date} is the {@code nth} workday of its month. */
    public static boolean isNthWorkday(LocalDate date, CalendarData calendar, int nth) {
        if (!calendar.isWorkday(date)) {
            return false;
        }
        return workdayIndexInMonth(date, calendar) == nth;
    }

    /** {@code true} when {@code date} is a workday and no later workday exists in its month. */
    public static boolean isLastWorkday(LocalDate date, CalendarData calendar) {
        if (!calendar.isWorkday(date)) {
            return false;
        }
        YearMonth month = YearMonth.from(date);
        for (LocalDate cursor = date.plusDays(1); YearMonth.from(cursor).equals(month); cursor = cursor.plusDays(1)) {
            if (calendar.isWorkday(cursor)) {
                return false;
            }
        }
        return true;
    }

    /** 1-based index of {@code date} among the workdays of its month; 0 when it is not a workday. */
    public static int workdayIndexInMonth(LocalDate date, CalendarData calendar) {
        if (!calendar.isWorkday(date)) {
            return 0;
        }
        int index = 0;
        for (LocalDate cursor = date.withDayOfMonth(1); !cursor.isAfter(date); cursor = cursor.plusDays(1)) {
            if (calendar.isWorkday(cursor)) {
                index++;
            }
        }
        return index;
    }
}
