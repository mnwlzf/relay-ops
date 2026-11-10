package com.relayops.scheduling.domain;

import com.relayops.shared.error.BusinessException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Day-level filter of a schedule.
 *
 * <p>{@code weekdays} is only meaningful for {@link DayFilterType#WEEKDAYS}, {@code daysOfMonth}
 * for {@link DayFilterType#MONTH_DAYS}, {@code nth} for {@link DayFilterType#MONTH_NTH_WORKDAY}
 * and {@code dates} for {@link DayFilterType#CUSTOM_DATES}.</p>
 *
 * <p>Some filters are date patterns that ignore holidays ({@code WEEKDAYS}, {@code MONTH_DAYS},
 * {@code CUSTOM_DATES}); holiday compensation can still apply to them when the task asks for it,
 * because the holiday policy is evaluated separately from the filter.</p>
 */
public record DayFilter(
        DayFilterType type,
        Set<DayOfWeek> weekdays,
        Set<Integer> daysOfMonth,
        Integer nth,
        Set<LocalDate> dates
) {

    public DayFilter {
        type = type == null ? DayFilterType.ALL : type;
        weekdays = weekdays == null ? Set.of() : Set.copyOf(weekdays);
        daysOfMonth = daysOfMonth == null ? Set.of() : Set.copyOf(new TreeSet<>(daysOfMonth));
        dates = dates == null ? Set.of() : Set.copyOf(dates);
    }

    public static DayFilter all() {
        return new DayFilter(DayFilterType.ALL, Set.of(), Set.of(), null, Set.of());
    }

    public static DayFilter workday() {
        return new DayFilter(DayFilterType.WORKDAY, Set.of(), Set.of(), null, Set.of());
    }

    public static DayFilter of(DayFilterType type, List<DayOfWeek> weekdays, Integer nth, List<LocalDate> dates) {
        return of(type, weekdays, List.of(), nth, dates);
    }

    public static DayFilter of(DayFilterType type, List<DayOfWeek> weekdays, List<Integer> daysOfMonth,
                               Integer nth, List<LocalDate> dates) {
        return new DayFilter(
                type,
                weekdays == null ? Set.of() : new LinkedHashSet<>(weekdays),
                daysOfMonth == null ? Set.of() : new LinkedHashSet<>(daysOfMonth),
                nth,
                dates == null ? Set.of() : new LinkedHashSet<>(dates));
    }

    /** Whether this filter needs work calendar data (holiday / make-up day knowledge). */
    public boolean needsCalendar() {
        return switch (type) {
            case WORKDAY, NON_WORKDAY, MONTH_NTH_WORKDAY, MONTH_LAST_WORKDAY -> true;
            case ALL, WEEKDAYS, MONTH_DAYS, CUSTOM_DATES -> false;
        };
    }

    /** Whether the filter is a date pattern rather than a workday predicate. */
    public boolean isDatePattern() {
        return type == DayFilterType.WEEKDAYS
                || type == DayFilterType.MONTH_DAYS
                || type == DayFilterType.CUSTOM_DATES;
    }

    public void validate() {
        switch (type) {
            case WEEKDAYS -> {
                if (weekdays.isEmpty()) {
                    throw invalid("dayFilter=WEEKDAYS 需要指定 weekdays");
                }
            }
            case MONTH_DAYS -> {
                if (daysOfMonth.isEmpty()) {
                    throw invalid("dayFilter=MONTH_DAYS 需要指定 days（1-31）");
                }
                for (Integer day : daysOfMonth) {
                    if (day == null || day < 1 || day > 31) {
                        throw invalid("dayFilter=MONTH_DAYS 的 days 必须是 1-31: " + day);
                    }
                }
            }
            case MONTH_NTH_WORKDAY -> {
                if (nth == null || nth < 1 || nth > 23) {
                    throw invalid("dayFilter=MONTH_NTH_WORKDAY 需要 1-23 之间的 nth");
                }
            }
            case CUSTOM_DATES -> {
                if (dates.isEmpty()) {
                    throw invalid("dayFilter=CUSTOM_DATES 需要指定 dates");
                }
            }
            default -> {
                // no extra configuration
            }
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("SCHEDULE_SPEC_INVALID", message);
    }
}
