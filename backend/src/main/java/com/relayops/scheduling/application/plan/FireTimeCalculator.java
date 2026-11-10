package com.relayops.scheduling.application.plan;

import com.relayops.scheduling.domain.ScheduleKind;
import com.relayops.scheduling.domain.day.CalendarData;
import com.relayops.scheduling.domain.day.DayMatcher;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.quartz.CronExpression;

/**
 * Computes the next fire times of a compiled schedule.
 *
 * <p>This mirrors what Quartz does with the same primitives (cron expression + calendar), so the
 * "next 10 runs" preview on the admin page matches the real schedule. The only intentional
 * difference is holiday compensation ({@code NEXT_WORKDAY} / {@code PREV_WORKDAY}), which is applied
 * by the application after a fire is skipped.</p>
 */
public final class FireTimeCalculator {

    /** Upper bound of cron evaluations per expression; protects preview from "never matches" filters. */
    private static final int MAX_CRON_STEPS = 5000;

    private FireTimeCalculator() {
    }

    public static List<Instant> nextFireTimes(
            CompiledSchedule schedule,
            CalendarData calendar,
            Instant after,
            int count
    ) {
        if (count <= 0) {
            return List.of();
        }
        CalendarData data = calendar == null ? CalendarData.empty(null) : calendar;
        return switch (schedule.kind()) {
            case ONCE -> schedule.once() != null && schedule.once().isAfter(after)
                    ? List.of(schedule.once())
                    : List.of();
            case INTERVAL -> intervalFireTimes(schedule, after, count);
            case CRON, CALENDAR_TIMES, MONTH_NTH_WORKDAY -> cronFireTimes(schedule, data, after, count);
        };
    }

    private static List<Instant> intervalFireTimes(CompiledSchedule schedule, Instant after, int count) {
        List<Instant> result = new ArrayList<>(count);
        Instant cursor = after;
        for (int i = 0; i < count; i++) {
            cursor = cursor.plus(schedule.interval());
            result.add(cursor);
        }
        return List.copyOf(result);
    }

    private static List<Instant> cronFireTimes(
            CompiledSchedule schedule,
            CalendarData calendar,
            Instant after,
            int count
    ) {
        Set<Instant> merged = new LinkedHashSet<>();
        for (CronExpression cron : schedule.cronExpressions()) {
            merged.addAll(fromCron(cron, schedule, calendar, after, count));
        }
        List<Instant> sorted = new ArrayList<>(merged);
        sorted.sort(Comparator.naturalOrder());
        return List.copyOf(sorted.subList(0, Math.min(count, sorted.size())));
    }

    private static List<Instant> fromCron(
            CronExpression cron,
            CompiledSchedule schedule,
            CalendarData calendar,
            Instant after,
            int limit
    ) {
        DayMatcher matcher = schedule.dayMatcher();
        List<Instant> result = new ArrayList<>(limit);
        Date cursor = Date.from(after);
        for (int step = 0; step < MAX_CRON_STEPS && result.size() < limit; step++) {
            Date next = cron.getNextValidTimeAfter(cursor);
            if (next == null) {
                break;
            }
            cursor = next;
            Instant instant = next.toInstant();
            LocalDate date = instant.atZone(schedule.zone()).toLocalDate();
            if (matcher.matches(date, calendar)) {
                result.add(instant);
            }
        }
        return result;
    }
}
