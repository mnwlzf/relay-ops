package com.relayops.scheduling.application.plan;

import com.relayops.scheduling.domain.HolidayPolicy;
import com.relayops.scheduling.domain.ScheduleKind;
import com.relayops.scheduling.domain.ScheduleSpec;
import com.relayops.scheduling.domain.day.DayMatcher;
import com.relayops.scheduling.domain.day.DayMatchers;
import com.relayops.shared.error.BusinessException;
import java.text.ParseException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.quartz.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Turns a {@link ScheduleSpec} into {@link CompiledSchedule}.
 *
 * <p>Every "day + time of day" schedule compiles to {@code 0 m h ? * *} cron expressions and lets
 * the day matcher (through a custom Quartz calendar) decide which dates are allowed. That keeps the
 * expression space tiny: no custom syntax for "first workday of the month".</p>
 */
@Component
public class ScheduleSpecCompiler {

    private final ScheduleSpecCodec codec;

    public ScheduleSpecCompiler(ScheduleSpecCodec codec) {
        this.codec = codec;
    }

    public CompiledSchedule compile(ScheduleSpec spec) {
        codec.validate(spec);
        DayMatcher matcher = matcherFor(spec);

        return switch (spec.kind()) {
            case CRON -> new CompiledSchedule(spec.kind(), spec.zone(),
                    List.of(cron(spec.cron(), spec.zone())), null, null, matcher);
            case INTERVAL -> new CompiledSchedule(spec.kind(), spec.zone(), List.of(),
                    spec.interval(), null, matcher);
            case ONCE -> new CompiledSchedule(spec.kind(), spec.zone(), List.of(),
                    null, spec.once(), matcher);
            case CALENDAR_TIMES, MONTH_NTH_WORKDAY -> new CompiledSchedule(spec.kind(), spec.zone(),
                    timeOfDayExpressions(spec.times(), spec.zone()), null, null, matcher);
        };
    }

    /**
     * Effective day matcher.
     *
     * <p>For {@code FORCE} filtering is off. For shift policies the matcher additionally requires a
     * workday, so a holiday Monday is skipped by the trigger and only the compensation fires.</p>
     */
    private DayMatcher matcherFor(ScheduleSpec spec) {
        if (spec.holidayPolicy() == HolidayPolicy.FORCE) {
            return (date, calendar) -> true;
        }
        DayMatcher base = DayMatchers.from(spec.dayFilter());
        if (!spec.shiftsToWorkday()) {
            return base;
        }
        return (date, calendar) -> base.matches(date, calendar) && calendar.isWorkday(date);
    }

    private List<CronExpression> timeOfDayExpressions(List<LocalTime> times, ZoneId zone) {
        List<CronExpression> expressions = new ArrayList<>(times.size());
        for (LocalTime time : times) {
            String expression = "%d %d %d ? * *".formatted(time.getSecond(), time.getMinute(), time.getHour());
            expressions.add(cron(expression, zone));
        }
        return List.copyOf(expressions);
    }

    private CronExpression cron(String expression, ZoneId zone) {
        try {
            CronExpression cron = new CronExpression(expression);
            cron.setTimeZone(TimeZone.getTimeZone(zone));
            return cron;
        } catch (ParseException ex) {
            throw new BusinessException("SCHEDULE_SPEC_INVALID", "Cron 表达式不合法: " + expression);
        }
    }
}
