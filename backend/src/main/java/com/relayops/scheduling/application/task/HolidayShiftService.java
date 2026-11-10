package com.relayops.scheduling.application.task;

import com.relayops.scheduling.adapter.out.persistence.ScheduledTaskRepository;
import com.relayops.scheduling.adapter.out.persistence.SchedulingCalendarRepository;
import com.relayops.scheduling.application.calendar.SchedulingCalendarRegistry;
import com.relayops.scheduling.config.SchedulingProperties;
import com.relayops.scheduling.domain.ScheduleSpec;
import com.relayops.scheduling.domain.TaskDefinition;
import com.relayops.scheduling.domain.TriggerSource;
import com.relayops.shared.time.BusinessTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Materializes {@link HolidayShiftPlanner} plans as one-shot Quartz triggers.
 *
 * <p>Shift triggers live in the task's trigger group with the prefix {@code shift-}, so rebuilding a
 * task (or the daily scan) can delete and recreate them without touching regular triggers.</p>
 */
@Service
public class HolidayShiftService {

    private static final Logger log = LoggerFactory.getLogger(HolidayShiftService.class);
    private static final String TRIGGER_PREFIX = "shift-";
    private static final DateTimeFormatter KEY_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.of("UTC"));

    private final Scheduler scheduler;
    private final ScheduledTaskRepository tasks;
    private final SchedulingCalendarRepository calendars;
    private final SchedulingCalendarRegistry registry;
    private final SchedulingProperties properties;
    private final BusinessTime time;

    public HolidayShiftService(Scheduler scheduler,
                               ScheduledTaskRepository tasks,
                               SchedulingCalendarRepository calendars,
                               SchedulingCalendarRegistry registry,
                               SchedulingProperties properties,
                               BusinessTime time) {
        this.scheduler = scheduler;
        this.tasks = tasks;
        this.calendars = calendars;
        this.registry = registry;
        this.properties = properties;
        this.time = time;
    }

    /** Recomputes the shift triggers of one task; safe to call repeatedly. */
    public void apply(TaskDefinition task) {
        ScheduleSpec spec = task.spec();
        deleteShiftTriggers(task.id());
        if (!HolidayShiftPlanner.applies(spec)) {
            return;
        }
        var calendar = registry.data(spec.calendarCode());
        List<HolidayShiftPlanner.ShiftPlan> plans = HolidayShiftPlanner.plan(
                spec, calendar, time.now(), properties.shiftHorizonDays());
        for (HolidayShiftPlanner.ShiftPlan plan : plans) {
            schedule(task, plan);
        }
        if (!plans.isEmpty()) {
            log.info("节假日顺延已登记: taskId={}, shifts={}", task.id(), plans.size());
        }
    }

    /** Recomputes shifts for every enabled task; runs shortly after midnight. */
    @Scheduled(cron = "0 5 0 * * ?")
    public void refreshAll() {
        if (!properties.schedulingEnabled()) {
            return;
        }
        int applied = 0;
        for (TaskDefinition task : tasks.findAll()) {
            if (!task.enabled() || !HolidayShiftPlanner.applies(task.spec())) {
                continue;
            }
            try {
                apply(task);
                applied++;
            } catch (RuntimeException ex) {
                log.warn("重算节假日顺延失败: taskId={}", task.id(), ex);
            }
        }
        if (applied > 0) {
            log.debug("节假日顺延扫描完成: tasks={}", applied);
        }
    }

    public void deleteShiftTriggers(long taskId) {
        String group = group(taskId);
        try {
            Set<TriggerKey> keys = scheduler.getTriggerKeys(
                    org.quartz.impl.matchers.GroupMatcher.triggerGroupEquals(group));
            for (TriggerKey key : keys) {
                if (key.getName().startsWith(TRIGGER_PREFIX)) {
                    scheduler.unscheduleJob(key);
                }
            }
        } catch (SchedulerException ex) {
            log.warn("清理节假日顺延触发器失败: taskId={}", taskId, ex);
        }
    }

    private void schedule(TaskDefinition task, HolidayShiftPlanner.ShiftPlan plan) {
        String name = TRIGGER_PREFIX + KEY_FORMAT.format(plan.shiftedAt());
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(name, group(task.id()))
                .forJob(new JobKey(String.valueOf(task.id()), group(task.id())))
                .startAt(Date.from(plan.shiftedAt()))
                .usingJobData("shiftFrom", plan.plannedDate().toString())
                .usingJobData("shiftSource", TriggerSource.HOLIDAY_SHIFT.name())
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0)
                        .withMisfireHandlingInstructionFireNow())
                .build();
        try {
            scheduler.scheduleJob(trigger);
            log.info("登记节假日顺延: taskId={}, planned={}, target={}, at={}",
                    task.id(), plan.plannedDate(), plan.targetDate(), plan.shiftedAt());
        } catch (SchedulerException ex) {
            log.warn("登记节假日顺延失败: taskId={}, at={}", task.id(), plan.shiftedAt(), ex);
        }
    }

    private String group(long taskId) {
        return "task-" + taskId;
    }

    /** Kept for diagnostics: the store is authoritative, this mirrors what an operator would check. */
    public int shiftCount(long taskId) {
        try {
            return (int) scheduler.getTriggerKeys(
                            org.quartz.impl.matchers.GroupMatcher.triggerGroupEquals(group(taskId)))
                    .stream()
                    .filter(key -> key.getName().startsWith(TRIGGER_PREFIX))
                    .count();
        } catch (SchedulerException ex) {
            return 0;
        }
    }

    /** Exposed for logging in tests/admin pages. */
    public Instant now() {
        return time.now();
    }
}
