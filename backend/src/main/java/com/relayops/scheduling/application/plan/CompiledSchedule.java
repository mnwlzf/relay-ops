package com.relayops.scheduling.application.plan;

import com.relayops.scheduling.domain.ScheduleKind;
import com.relayops.scheduling.domain.day.DayMatcher;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.quartz.CronExpression;

/**
 * Normalized form of a schedule: the primitives Quartz needs, plus the day predicate used by both
 * the Quartz calendar and the preview.
 *
 * @param cronExpressions one entry per time of day (or a single expression for {@code CRON})
 * @param interval        set for {@code INTERVAL}
 * @param once            set for {@code ONCE}
 */
public record CompiledSchedule(
        ScheduleKind kind,
        ZoneId zone,
        List<CronExpression> cronExpressions,
        Duration interval,
        Instant once,
        DayMatcher dayMatcher
) {

    public CompiledSchedule {
        cronExpressions = cronExpressions == null ? List.of() : List.copyOf(cronExpressions);
    }

    public boolean isOneShot() {
        return kind == ScheduleKind.ONCE;
    }

    public boolean isInterval() {
        return kind == ScheduleKind.INTERVAL;
    }

    public boolean isCronBased() {
        return !cronExpressions.isEmpty();
    }
}
