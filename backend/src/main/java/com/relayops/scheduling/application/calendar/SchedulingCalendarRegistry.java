package com.relayops.scheduling.application.calendar;

import com.relayops.scheduling.adapter.out.persistence.SchedulingCalendarRepository;
import com.relayops.scheduling.domain.ScheduleSpec;
import com.relayops.scheduling.domain.day.CalendarData;
import com.relayops.scheduling.domain.day.DayMatcher;
import com.relayops.scheduling.domain.day.DayMatchers;
import com.relayops.shared.error.BusinessException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registers workday calendars with Quartz and hands out their names.
 *
 * <p>Quartz does not persist calendar objects, only trigger references to calendar names, so every
 * calendar must be re-registered on startup. Editing calendar data calls {@link #refresh()} which
 * re-adds each calendar with {@code replace = true, updateTriggers = true}.</p>
 */
@Component
public class SchedulingCalendarRegistry {

    private static final Logger log = LoggerFactory.getLogger(SchedulingCalendarRegistry.class);

    private final SchedulingCalendarRepository repository;
    private final Scheduler scheduler;
    private final Map<String, Registration> registrations = new ConcurrentHashMap<>();

    public SchedulingCalendarRegistry(SchedulingCalendarRepository repository, Scheduler scheduler) {
        this.repository = repository;
        this.scheduler = scheduler;
    }

    /**
     * Makes sure the day filter required by {@code spec} is registered as a Quartz calendar and
     * returns its name.
     *
     * <p>The matcher comes from the compiled schedule, never rebuilt here: registration, preview and
     * execution must use exactly the same predicate.</p>
     *
     * @return the calendar name, or {@code null} when the schedule does not filter days at all
     */
    public String resolve(ScheduleSpec spec, DayMatcher matcher) {
        if (!spec.usesDayMatcher()) {
            return null;
        }
        String name = calendarName(spec);
        CalendarData data = spec.needsCalendarData()
                ? repository.load(spec.calendarCode())
                : CalendarData.empty(spec.calendarCode());
        register(spec, matcher, data, name);
        return name;
    }

    /** Calendar data used by the preview, so preview and execution never disagree. */
    public CalendarData data(String calendarCode) {
        return repository.load(calendarCode);
    }

    /** Re-registers every calendar after its data changed. */
    public void refresh() {
        for (Map.Entry<String, Registration> entry : registrations.entrySet()) {
            Registration registration = entry.getValue();
            CalendarData data = registration.spec().needsCalendarData()
                    ? repository.load(registration.spec().calendarCode())
                    : CalendarData.empty(registration.spec().calendarCode());
            register(registration.spec(), registration.matcher(), data, entry.getKey());
        }
        log.info("工作日历已刷新: count={}", registrations.size());
    }

    /** Drops the cached registration for a calendar code after its days changed. */
    public void invalidate(String calendarCode) {
        registrations.entrySet().removeIf(entry ->
                calendarCode.equals(entry.getValue().spec().calendarCode()));
    }

    public int size() {
        return registrations.size();
    }

    /** Deterministic Quartz calendar name for a (calendar code, day filter) pair. */
    public static String calendarName(ScheduleSpec spec) {
        StringBuilder signature = new StringBuilder()
                .append(spec.calendarCode()).append('|')
                .append(spec.dayFilter().type()).append('|')
                .append(spec.dayFilter().nth()).append('|')
                .append(spec.dayFilter().weekdays().stream().sorted().toList()).append('|')
                .append(spec.dayFilter().daysOfMonth().stream().sorted().toList()).append('|')
                .append(spec.dayFilter().dates().stream().sorted().toList());
        return "sched-" + Integer.toHexString(signature.toString().hashCode());
    }

    private void register(ScheduleSpec spec, DayMatcher matcher, CalendarData data, String name) {
        WorkdayQuartzCalendar calendar = new WorkdayQuartzCalendar(
                matcher, data, spec.zone(), "day matcher " + spec.dayFilter().type() + " / " + spec.calendarCode());
        try {
            scheduler.addCalendar(name, calendar, true, true);
            registrations.put(name, new Registration(spec, matcher, calendar));
        } catch (SchedulerException ex) {
            throw new BusinessException("SCHEDULING_UNAVAILABLE", "注册工作日历失败: " + ex.getMessage(), ex);
        }
    }

    private record Registration(ScheduleSpec spec, DayMatcher matcher, WorkdayQuartzCalendar calendar) {
    }
}
