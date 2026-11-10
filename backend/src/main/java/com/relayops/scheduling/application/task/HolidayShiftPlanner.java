package com.relayops.scheduling.application.task;

import com.relayops.scheduling.domain.DayFilter;
import com.relayops.scheduling.domain.HolidayPolicy;
import com.relayops.scheduling.domain.ScheduleKind;
import com.relayops.scheduling.domain.ScheduleSpec;
import com.relayops.scheduling.domain.day.CalendarData;
import com.relayops.scheduling.domain.day.DayMatcher;
import com.relayops.scheduling.domain.day.DayMatchers;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes holiday compensation: when the calendar blocks a day the pattern wanted, decide whether an
 * extra "make-up" fire is needed and where it goes.
 *
 * <p>Two rules keep this from becoming noise:</p>
 * <ol>
 *   <li>A shift is only scheduled when the target day has <em>no</em> regular fire at the same time.
 *       "Workdays at 09:00" therefore never produces a duplicate: the first workday after a holiday
 *       already fires on its own.</li>
 *   <li>Several blocked days that map to the same target time collapse into one shift.</li>
 * </ol>
 *
 * <p>The effective predicate here is the same one the compiled schedule uses (pattern and, for shift
 * policies, "must be a workday"), so a holiday Monday is neither fired normally nor double-fired.</p>
 */
public final class HolidayShiftPlanner {

    private static final int MAX_LOOKAHEAD_DAYS = 30;
    private static final int MAX_PLANS = 50;

    private HolidayShiftPlanner() {
    }

    /** Whether the spec can produce holiday compensation at all. */
    public static boolean applies(ScheduleSpec spec) {
        return spec != null && spec.kind() == ScheduleKind.CALENDAR_TIMES && spec.shiftsToWorkday();
    }

    public static List<ShiftPlan> plan(ScheduleSpec spec, CalendarData calendar,
                                       Instant from, int horizonDays) {
        if (!applies(spec)) {
            return List.of();
        }
        CalendarData data = calendar == null ? CalendarData.empty(spec.calendarCode()) : calendar;
        DayMatcher base = DayMatchers.from(spec.dayFilter());
        DayMatcher effective = (date, cal) -> base.matches(date, cal) && cal.isWorkday(date);
        ZoneId zone = spec.zone();
        LocalDate start = from.atZone(zone).toLocalDate();
        int horizon = Math.min(Math.max(horizonDays, 1), MAX_LOOKAHEAD_DAYS);
        Map<Instant, ShiftPlan> plans = new LinkedHashMap<>();

        for (int offset = 0; offset < horizon && plans.size() < MAX_PLANS; offset++) {
            LocalDate date = start.plusDays(offset);
            if (!patternWants(spec.dayFilter(), date)) {
                continue;
            }
            if (effective.matches(date, data)) {
                continue; // fires normally
            }
            LocalDate target = resolveTarget(spec.holidayPolicy(), data, date);
            if (target == null) {
                continue;
            }
            for (LocalTime time : spec.times()) {
                if (effective.matches(target, data)) {
                    continue; // the regular schedule already covers this instant
                }
                Instant shiftedAt = target.atTime(time).atZone(zone).toInstant();
                if (!shiftedAt.isAfter(from)) {
                    continue;
                }
                plans.putIfAbsent(shiftedAt, new ShiftPlan(date, time, target, shiftedAt));
            }
        }
        List<ShiftPlan> ordered = new ArrayList<>(plans.values());
        ordered.sort((left, right) -> left.shiftedAt().compareTo(right.shiftedAt()));
        return List.copyOf(ordered);
    }

    /**
     * Whether the schedule's own pattern wants this date, ignoring holidays.
     *
     * <p>For {@code WORKDAY} that means "Monday to Friday", which is what "the intended day" means
     * before the calendar says it is a holiday.</p>
     */
    static boolean patternWants(DayFilter filter, LocalDate date) {
        return switch (filter.type()) {
            case ALL -> true;
            case WORKDAY -> !isWeekend(date);
            case NON_WORKDAY -> isWeekend(date);
            case WEEKDAYS -> filter.weekdays().contains(date.getDayOfWeek());
            case MONTH_DAYS -> filter.daysOfMonth().contains(date.getDayOfMonth());
            case CUSTOM_DATES -> filter.dates().contains(date);
            case MONTH_NTH_WORKDAY, MONTH_LAST_WORKDAY -> true;
        };
    }

    private static LocalDate resolveTarget(HolidayPolicy policy, CalendarData calendar, LocalDate date) {
        boolean forward = policy == HolidayPolicy.NEXT_WORKDAY;
        LocalDate cursor = date;
        for (int step = 0; step < MAX_LOOKAHEAD_DAYS; step++) {
            cursor = forward ? cursor.plusDays(1) : cursor.minusDays(1);
            if (calendar.isWorkday(cursor)) {
                return cursor;
            }
        }
        return null;
    }

    private static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /**
     * One make-up fire.
     *
     * @param plannedDate the date the pattern wanted
     * @param time        the time of day from the spec
     * @param targetDate  the workday the fire moved to
     * @param shiftedAt   the resulting instant
     */
    public record ShiftPlan(LocalDate plannedDate, LocalTime time, LocalDate targetDate, Instant shiftedAt) {
    }
}
