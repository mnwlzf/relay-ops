package com.relayops.scheduling.application.task;

import com.relayops.scheduling.adapter.out.persistence.ScheduledTaskRepository;
import com.relayops.scheduling.application.calendar.SchedulingCalendarRegistry;
import com.relayops.scheduling.application.execution.ScheduledTaskJob;
import com.relayops.scheduling.application.plan.CompiledSchedule;
import com.relayops.scheduling.application.plan.ScheduleSpecCompiler;
import com.relayops.scheduling.domain.MisfirePolicy;
import com.relayops.scheduling.domain.TaskDefinition;
import com.relayops.scheduling.domain.TriggerSource;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.time.BusinessTime;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Keeps Quartz triggers in sync with the {@code scheduling_task} table.
 *
 * <p>Single writer for scheduler state: the admin service, the bootstrap and the retry path all go
 * through here, so DB and scheduler never drift.</p>
 */
@Service
public class TaskRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(TaskRegistrationService.class);
    private static final String GROUP_PREFIX = "task-";

    private final Scheduler scheduler;
    private final ScheduleSpecCompiler compiler;
    private final SchedulingCalendarRegistry calendars;
    private final ScheduledTaskRepository repository;
    private final BusinessTime time;
    private final HolidayShiftService holidayShifts;

    public TaskRegistrationService(Scheduler scheduler,
                                   ScheduleSpecCompiler compiler,
                                   SchedulingCalendarRegistry calendars,
                                   ScheduledTaskRepository repository,
                                   BusinessTime time,
                                   HolidayShiftService holidayShifts) {
        this.scheduler = scheduler;
        this.compiler = compiler;
        this.calendars = calendars;
        this.repository = repository;
        this.time = time;
        this.holidayShifts = holidayShifts;
    }

    /** Rebuilds all triggers of a task; a disabled task ends up with no triggers at all. */
    public void register(TaskDefinition task) {
        JobKey jobKey = jobKey(task.id());
        try {
            if (scheduler.checkExists(jobKey)) {
                scheduler.deleteJob(jobKey);
            }
            if (!task.enabled()) {
                repository.updateNextFireAt(task.id(), null);
                return;
            }
            CompiledSchedule compiled = compiler.compile(task.spec());
            String calendarName = calendars.resolve(task.spec(), compiled.dayMatcher());
            JobDetail job = JobBuilder.newJob(ScheduledTaskJob.class)
                    .withIdentity(jobKey)
                    .usingJobData("taskId", String.valueOf(task.id()))
                    .build();
            Set<Trigger> triggers = buildTriggers(task, compiled, calendarName);
            scheduler.scheduleJob(job, triggers, true);
            holidayShifts.apply(task);
            refreshNextFire(task.id());
            log.info("定时任务已注册: id={}, name={}, kind={}, triggers={}, calendar={}",
                    task.id(), task.name(), task.spec().kind(), triggers.size(), calendarName);
        } catch (SchedulerException ex) {
            throw new BusinessException("SCHEDULING_UNAVAILABLE", "注册定时任务失败: " + ex.getMessage(), ex);
        }
    }

    public void unregister(long taskId) {
        try {
            scheduler.deleteJob(jobKey(taskId));
            repository.updateNextFireAt(taskId, null);
        } catch (SchedulerException ex) {
            throw new BusinessException("SCHEDULING_UNAVAILABLE", "移除定时任务失败: " + ex.getMessage(), ex);
        }
    }

    /** DB is the source of truth: delete orphan triggers, register missing ones, refresh calendars. */
    public void resync() {
        List<TaskDefinition> tasks = repository.findAll();
        Map<Long, TaskDefinition> byId = tasks.stream()
                .collect(java.util.stream.Collectors.toMap(TaskDefinition::id, task -> task, (left, right) -> left));
        try {
            Set<JobKey> existing = new LinkedHashSet<>(scheduler.getJobKeys(GroupMatcher.anyJobGroup()));
            for (JobKey key : existing) {
                if (!key.getGroup().startsWith(GROUP_PREFIX)) {
                    continue;
                }
                long taskId = Long.parseLong(key.getName());
                TaskDefinition task = byId.get(taskId);
                if (task == null || !task.enabled()) {
                    scheduler.deleteJob(key);
                    log.info("清理孤儿任务触发器: taskId={}", taskId);
                }
            }
            for (TaskDefinition task : tasks) {
                if (task.enabled()) {
                    register(task);
                }
            }
            calendars.refresh();
        } catch (SchedulerException ex) {
            throw new BusinessException("SCHEDULING_UNAVAILABLE", "调度对账失败: " + ex.getMessage(), ex);
        }
    }

    /** Schedules an extra one-shot run without touching the regular triggers. */
    public void scheduleOnce(long taskId, Instant at, TriggerSource source, String suffix) {
        try {
            JobKey jobKey = jobKey(taskId);
            if (!scheduler.checkExists(jobKey)) {
                log.warn("任务不存在于调度器，跳过一次性触发: taskId={}", taskId);
                return;
            }
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("once-" + suffix, groupName(taskId))
                    .forJob(jobKey)
                    .startAt(Date.from(at))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0)
                            .withMisfireHandlingInstructionFireNow())
                    .build();
            scheduler.scheduleJob(trigger);
            log.info("已登记一次性触发: taskId={}, at={}, source={}", taskId, at, source);
        } catch (SchedulerException ex) {
            throw new BusinessException("SCHEDULING_UNAVAILABLE", "登记一次性触发失败: " + ex.getMessage(), ex);
        }
    }

    /** Reads the next fire time from Quartz and mirrors it into the task row. */
    public Instant refreshNextFire(long taskId) {
        Instant earliest = null;
        try {
            for (Trigger trigger : scheduler.getTriggersOfJob(jobKey(taskId))) {
                Date next = trigger.getNextFireTime();
                if (next == null) {
                    continue;
                }
                Instant candidate = next.toInstant();
                if (earliest == null || candidate.isBefore(earliest)) {
                    earliest = candidate;
                }
            }
        } catch (SchedulerException ex) {
            log.warn("读取下一次触发时间失败: taskId={}", taskId, ex);
        }
        repository.updateNextFireAt(taskId, earliest);
        return earliest;
    }

    public Instant nextFireTime(long taskId) {
        try {
            Instant earliest = null;
            for (Trigger trigger : scheduler.getTriggersOfJob(jobKey(taskId))) {
                Date next = trigger.getNextFireTime();
                if (next == null) {
                    continue;
                }
                Instant candidate = next.toInstant();
                if (earliest == null || candidate.isBefore(earliest)) {
                    earliest = candidate;
                }
            }
            return earliest;
        } catch (SchedulerException ex) {
            return null;
        }
    }

    private Set<Trigger> buildTriggers(TaskDefinition task, CompiledSchedule compiled, String calendarName) {
        Set<Trigger> triggers = new LinkedHashSet<>();
        String group = groupName(task.id());
        TimeZone zone = TimeZone.getTimeZone(task.spec().zone());
        switch (task.spec().kind()) {
            case CRON -> triggers.add(cronTrigger(task, compiled.cronExpressions().get(0).getCronExpression(),
                    group, 0, calendarName, zone));
            case CALENDAR_TIMES, MONTH_NTH_WORKDAY -> {
                int index = 0;
                for (var expression : compiled.cronExpressions()) {
                    triggers.add(cronTrigger(task, expression.getCronExpression(), group, index++, calendarName, zone));
                }
            }
            case INTERVAL -> triggers.add(intervalTrigger(task, group, zone));
            case ONCE -> triggers.add(onceTrigger(task, group));
        }
        return triggers;
    }

    private Trigger cronTrigger(TaskDefinition task, String expression, String group, int index,
                                String calendarName, TimeZone zone) {
        CronScheduleBuilder schedule = CronScheduleBuilder.cronSchedule(expression).inTimeZone(zone);
        schedule = task.misfirePolicy() == MisfirePolicy.SKIP
                ? schedule.withMisfireHandlingInstructionDoNothing()
                : schedule.withMisfireHandlingInstructionFireAndProceed();
        var builder = TriggerBuilder.newTrigger()
                .withIdentity("t" + index, group)
                .withSchedule(schedule)
                .startNow();
        if (calendarName != null) {
            builder = builder.modifiedByCalendar(calendarName);
        }
        return builder.build();
    }

    private Trigger intervalTrigger(TaskDefinition task, String group, TimeZone zone) {
        SimpleScheduleBuilder schedule = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMilliseconds(task.spec().interval().toMillis())
                .repeatForever();
        schedule = task.misfirePolicy() == MisfirePolicy.SKIP
                ? schedule.withMisfireHandlingInstructionNextWithRemainingCount()
                : schedule.withMisfireHandlingInstructionFireNow();
        return TriggerBuilder.newTrigger()
                .withIdentity("t0", group)
                .withSchedule(schedule)
                .startAt(Date.from(time.now().plus(task.spec().interval())))
                .build();
    }

    private Trigger onceTrigger(TaskDefinition task, String group) {
        SimpleScheduleBuilder schedule = SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0)
                .withMisfireHandlingInstructionFireNow();
        return TriggerBuilder.newTrigger()
                .withIdentity("t0", group)
                .withSchedule(schedule)
                .startAt(Date.from(task.spec().once()))
                .build();
    }

    private JobKey jobKey(long taskId) {
        return new JobKey(String.valueOf(taskId), groupName(taskId));
    }

    private String groupName(long taskId) {
        return GROUP_PREFIX + taskId;
    }
}
