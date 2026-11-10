package com.relayops.scheduling.application.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.relayops.scheduling.adapter.out.persistence.ScheduledTaskRepository;
import com.relayops.scheduling.adapter.out.persistence.ScheduledTaskRunRepository;
import com.relayops.scheduling.adapter.out.persistence.SchedulingCalendarRepository;
import com.relayops.scheduling.application.calendar.CalendarImportParser;
import com.relayops.scheduling.application.calendar.SchedulingCalendarRegistry;
import com.relayops.scheduling.application.execution.TaskExecutionService;
import com.relayops.scheduling.application.plan.CompiledSchedule;
import com.relayops.scheduling.application.plan.FireTimeCalculator;
import com.relayops.scheduling.application.plan.ScheduleSpecCodec;
import com.relayops.scheduling.application.plan.ScheduleSpecCompiler;
import com.relayops.scheduling.config.SchedulingProperties;
import com.relayops.scheduling.contract.ScheduledTaskHandler;
import com.relayops.scheduling.domain.ScheduleSpec;
import com.relayops.scheduling.domain.TaskDefinition;
import com.relayops.scheduling.domain.TaskRunRecord;
import com.relayops.scheduling.domain.TriggerSource;
import com.relayops.scheduling.domain.day.CalendarData;
import com.relayops.scheduling.domain.day.DayType;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.time.BusinessTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin use cases: task CRUD, preview, runs, calendars and reconciliation. */
@Service
public class SchedulingAdminService {

    private static final int DEFAULT_RUN_LIMIT = 20;

    private final ScheduledTaskRepository tasks;
    private final ScheduledTaskRunRepository runs;
    private final SchedulingCalendarRepository calendars;
    private final SchedulingCalendarRegistry calendarRegistry;
    private final ScheduleSpecCodec codec;
    private final ScheduleSpecCompiler compiler;
    private final com.relayops.scheduling.application.execution.ScheduledTaskHandlerRegistry handlers;
    private final TaskRegistrationService registration;
    private final TaskExecutionService execution;
    private final HolidayShiftService holidayShifts;
    private final SchedulingProperties properties;
    private final BusinessTime time;
    private final ExecutorService executor;

    public SchedulingAdminService(ScheduledTaskRepository tasks,
                                  ScheduledTaskRunRepository runs,
                                  SchedulingCalendarRepository calendars,
                                  SchedulingCalendarRegistry calendarRegistry,
                                  ScheduleSpecCodec codec,
                                  ScheduleSpecCompiler compiler,
                                  com.relayops.scheduling.application.execution.ScheduledTaskHandlerRegistry handlers,
                                  TaskRegistrationService registration,
                                  TaskExecutionService execution,
                                  HolidayShiftService holidayShifts,
                                  SchedulingProperties properties,
                                  BusinessTime time,
                                  @Qualifier("schedulingTaskExecutor") ExecutorService executor) {
        this.tasks = tasks;
        this.runs = runs;
        this.calendars = calendars;
        this.calendarRegistry = calendarRegistry;
        this.codec = codec;
        this.compiler = compiler;
        this.handlers = handlers;
        this.registration = registration;
        this.execution = execution;
        this.holidayShifts = holidayShifts;
        this.properties = properties;
        this.time = time;
        this.executor = executor;
    }

    public List<TaskDefinition> list() {
        return tasks.findAll();
    }

    public TaskDefinition get(long id) {
        return tasks.get(id);
    }

    public List<ScheduledTaskHandler> handlers() {
        return handlers.all();
    }

    @Transactional
    public TaskDefinition create(TaskCommand command) {
        ScheduleSpec spec = codec.parse(command.spec());
        validate(command, spec);
        TaskDefinition draft = new TaskDefinition(null, command.taskType(), command.name(), spec,
                command.params() == null ? JsonNodeFactory.instance.objectNode() : command.params(),
                command.misfirePolicy(), command.maxCatchUp(), command.concurrencyPolicy(),
                command.timeoutSeconds(), command.retryMax(), command.retryBackoffSeconds(),
                command.quietHours(), command.enabled(), null, null, null, null, 0, null, null);
        TaskDefinition saved;
        try {
            saved = tasks.insert(draft);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("SCHEDULING_NAME_DUPLICATED", "任务名称已存在: " + command.name());
        }
        try {
            registration.register(saved);
        } catch (RuntimeException ex) {
            tasks.delete(saved.id());
            throw ex;
        }
        return tasks.get(saved.id());
    }

    @Transactional
    public TaskDefinition update(long id, TaskCommand command) {
        TaskDefinition existing = tasks.get(id);
        ScheduleSpec spec = codec.parse(command.spec());
        validate(command, spec);
        TaskDefinition draft = new TaskDefinition(existing.id(), command.taskType(), command.name(), spec,
                command.params() == null ? JsonNodeFactory.instance.objectNode() : command.params(),
                command.misfirePolicy(), command.maxCatchUp(), command.concurrencyPolicy(),
                command.timeoutSeconds(), command.retryMax(), command.retryBackoffSeconds(),
                command.quietHours(), command.enabled(), existing.nextFireAt(), existing.lastRunAt(),
                existing.lastRunStatus(), existing.lastRunMessage(), existing.version(),
                existing.createdAt(), existing.updatedAt());
        TaskDefinition saved;
        try {
            saved = tasks.update(draft);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("SCHEDULING_NAME_DUPLICATED", "任务名称已存在: " + command.name());
        }
        registration.register(saved);
        return tasks.get(saved.id());
    }

    @Transactional
    public TaskDefinition toggle(long id, boolean enabled) {
        tasks.get(id);
        tasks.updateEnabled(id, enabled);
        TaskDefinition updated = tasks.get(id);
        registration.register(updated);
        return tasks.get(id);
    }

    @Transactional
    public void delete(long id) {
        tasks.get(id);
        registration.unregister(id);
        tasks.delete(id);
    }

    public void runNow(long id) {
        tasks.get(id);
        Instant plannedAt = time.now();
        executor.submit(() -> execution.run(id, plannedAt, TriggerSource.MANUAL));
    }

    public List<TaskRunRecord> runs(long id, Integer limit) {
        tasks.get(id);
        int size = limit == null ? DEFAULT_RUN_LIMIT : Math.min(Math.max(limit, 1), 200);
        return runs.recent(id, size);
    }

    /**
     * Computes the next fire times of a spec without saving it, using the same compiler and day
     * matcher as execution.
     */
    public List<PreviewEntry> preview(JsonNode specNode, Integer count) {
        ScheduleSpec spec = codec.parse(specNode);
        CompiledSchedule compiled = compiler.compile(spec);
        CalendarData data = spec.calendarCode() == null ? CalendarData.empty(null) : calendars.load(spec.calendarCode());
        int size = count == null ? properties.previewCount() : Math.min(Math.max(count, 1), 100);
        return FireTimeCalculator.nextFireTimes(compiled, data, time.now(), size).stream()
                .map(instant -> new PreviewEntry(
                        instant,
                        instant.atZone(spec.zone()).toLocalDateTime().toString(),
                        data.typeOf(instant.atZone(spec.zone()).toLocalDate()).name()))
                .toList();
    }

    public List<CalendarView> calendars() {
        LocalDate today = LocalDate.now(time.zone());
        return calendars.findAll().stream()
                .sorted(Comparator.comparing(SchedulingCalendarRepository.CalendarInfo::code))
                .map(info -> new CalendarView(info.id(), info.code(), info.name(), info.timezone(),
                        info.source(), info.coveredTo(), info.description(), calendars.countDays(info.code()),
                        coverageWarning(info.coveredTo(), today)))
                .toList();
    }

    /**
     * Warns before the calendar runs out of data: an un-imported year silently reintroduces the
     * "Monday to Friday" default, which is wrong around Spring Festival.
     */
    private String coverageWarning(LocalDate coveredTo, LocalDate today) {
        if (coveredTo == null) {
            return "尚未导入任何日期，全部按周一至周五为工作日计算";
        }
        if (coveredTo.isBefore(today.plusDays(60))) {
            return "数据仅覆盖到 " + coveredTo + "，请导入 " + (coveredTo.getYear() + 1) + " 年放假安排";
        }
        return null;
    }

    public List<SchedulingCalendarRepository.CalendarDay> calendarDays(String code, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now(time.zone()).withDayOfMonth(1) : from;
        LocalDate end = to == null ? start.plusYears(1).minusDays(1) : to;
        return calendars.days(code, start, end);
    }

    @Transactional
    public List<SchedulingCalendarRepository.CalendarDay> upsertDays(String code, List<CalendarDayCommand> days,
                                                                    String origin) {
        calendars.get(code);
        List<SchedulingCalendarRepository.CalendarDay> rows = days.stream()
                .map(day -> new SchedulingCalendarRepository.CalendarDay(
                        day.day(), parseDayType(day.dayType()), day.name(), origin))
                .toList();
        calendars.upsertDays(code, rows);
        reapplyCalendar(code);
        return calendarDays(code, null, null);
    }

    @Transactional
    public List<SchedulingCalendarRepository.CalendarDay> importRaw(String code, String content, String format) {
        calendars.get(code);
        CalendarImportParser.Format resolved;
        try {
            resolved = format == null || format.isBlank()
                    ? CalendarImportParser.Format.AUTO
                    : CalendarImportParser.Format.valueOf(format.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("SCHEDULING_CALENDAR_INVALID", "未知的导入格式: " + format);
        }
        List<CalendarImportParser.ImportedDay> imported = CalendarImportParser.parse(content, resolved);
        List<CalendarDayCommand> commands = imported.stream()
                .map(day -> new CalendarDayCommand(day.day(), day.dayType().name(), day.name()))
                .toList();
        return upsertDays(code, commands, "IMPORT");
    }

    @Transactional
    public void deleteDay(String code, LocalDate day) {
        calendars.get(code);
        calendars.deleteDay(code, day);
        reapplyCalendar(code);
    }

    public void resync() {
        registration.resync();
    }

    private void reapplyCalendar(String code) {
        calendarRegistry.refresh();
        for (TaskDefinition task : tasks.findAll()) {
            if (!code.equals(task.spec().calendarCode())) {
                continue;
            }
            holidayShifts.apply(task);
            registration.refreshNextFire(task.id());
        }
    }

    private void validate(TaskCommand command, ScheduleSpec spec) {
        if (command.name() == null || command.name().isBlank()) {
            throw new BusinessException("SCHEDULING_TASK_INVALID", "任务名称不能为空");
        }
        ScheduledTaskHandler handler = handlers.find(command.taskType())
                .orElseThrow(() -> new BusinessException("SCHEDULING_HANDLER_UNKNOWN",
                        "未注册的任务类型: " + command.taskType()));
        if (!handler.describe().supports(spec.kind())) {
            throw new BusinessException("SCHEDULING_KIND_UNSUPPORTED",
                    "任务类型 " + command.taskType() + " 不支持调度方式 " + spec.kind());
        }
        if (spec.calendarCode() != null) {
            calendars.get(spec.calendarCode());
        }
    }

    private DayType parseDayType(String value) {
        try {
            return DayType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new BusinessException("SCHEDULING_CALENDAR_INVALID", "未知的日期类型: " + value);
        }
    }

    /** One computed fire time, annotated with the day type so the UI can explain holiday shifts. */
    public record PreviewEntry(Instant fireAt, String localDateTime, String dayType) {
    }

    /** Calendar header plus the number of stored exception days. */
    public record CalendarView(Long id, String code, String name, String timezone, String source,
                               LocalDate coveredTo, String description, int exceptionDays,
                               String coverageWarning) {
    }

    /** Input of a calendar day edit. */
    public record CalendarDayCommand(LocalDate day, String dayType, String name) {
    }
}
