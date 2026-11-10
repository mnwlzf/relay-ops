package com.relayops.scheduling.domain;

import com.relayops.shared.time.BusinessTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Structured schedule definition stored as JSON in {@code scheduling_task.spec}.
 *
 * <p>Field usage per {@link ScheduleKind}:</p>
 * <ul>
 *   <li>{@code CRON}: {@code cron}</li>
 *   <li>{@code INTERVAL}: {@code interval}</li>
 *   <li>{@code CALENDAR_TIMES}, {@code MONTH_NTH_WORKDAY}: {@code times}, {@code dayFilter}, {@code calendarCode}</li>
 *   <li>{@code ONCE}: {@code once}</li>
 * </ul>
 */
public record ScheduleSpec(
        ScheduleKind kind,
        ZoneId zone,
        String calendarCode,
        DayFilter dayFilter,
        List<LocalTime> times,
        HolidayPolicy holidayPolicy,
        ActiveWindow activeWindow,
        String cron,
        Duration interval,
        Instant once
) {

    public ScheduleSpec {
        zone = zone == null ? BusinessTime.DEFAULT_ZONE : zone;
        dayFilter = dayFilter == null ? DayFilter.all() : dayFilter;
        holidayPolicy = holidayPolicy == null ? HolidayPolicy.SKIP : holidayPolicy;
        times = normalize(times);
        calendarCode = calendarCode == null || calendarCode.isBlank() ? null : calendarCode.trim();
        cron = cron == null || cron.isBlank() ? null : cron.trim();
    }

    /** Inclusive local time window used to validate {@code times} for time-of-day schedules. */
    public record ActiveWindow(LocalTime from, LocalTime to) {

        public ActiveWindow {
            if (from == null || to == null) {
                throw new com.relayops.shared.error.BusinessException(
                        "SCHEDULE_SPEC_INVALID", "activeWindow 需要 from/to");
            }
            if (to.isBefore(from)) {
                throw new com.relayops.shared.error.BusinessException(
                        "SCHEDULE_SPEC_INVALID", "activeWindow 的 to 不能早于 from");
            }
        }

        public boolean contains(LocalTime time) {
            return !time.isBefore(from) && !time.isAfter(to);
        }
    }

    /** Whether day-level filtering applies (i.e. the schedule is not a pure cadence). */
    public boolean calendarAware() {
        return kind == ScheduleKind.CALENDAR_TIMES || kind == ScheduleKind.MONTH_NTH_WORKDAY;
    }

    /**
     * Whether a day matcher must be attached to the triggers.
     *
     * <p>True even when the filter is a pure date pattern (weekdays, days of month, explicit dates):
     * without the matcher Quartz would fire on every day. {@code FORCE} is the only case that
     * deliberately bypasses day filtering.</p>
     */
    public boolean usesDayMatcher() {
        return calendarAware() && holidayPolicy != HolidayPolicy.FORCE;
    }

    /** Whether the day matcher needs work calendar data (holiday / make-up day knowledge). */
    public boolean needsCalendarData() {
        return usesDayMatcher() && dayFilter.needsCalendar();
    }

    /**
     * Whether a blocked day should be shifted onto a workday.
     *
     * <p>Only meaningful for schedules whose intended days are known in advance: fixed weekdays,
     * days of the month, explicit dates and the workday filter itself. When enabled, the effective
     * day matcher also requires the day to be a workday, so a holiday Monday neither fires on its
     * own nor gets duplicated by the compensation.</p>
     */
    public boolean shiftsToWorkday() {
        if (kind != ScheduleKind.CALENDAR_TIMES) {
            return false;
        }
        if (holidayPolicy != HolidayPolicy.NEXT_WORKDAY && holidayPolicy != HolidayPolicy.PREV_WORKDAY) {
            return false;
        }
        return switch (dayFilter.type()) {
            case WORKDAY, WEEKDAYS, MONTH_DAYS, CUSTOM_DATES -> true;
            case ALL, NON_WORKDAY, MONTH_NTH_WORKDAY, MONTH_LAST_WORKDAY -> false;
        };
    }

    private static List<LocalTime> normalize(List<LocalTime> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<LocalTime> ordered = new LinkedHashSet<>(values);
        return List.copyOf(new java.util.TreeSet<>(ordered));
    }
}
