package com.relayops.scheduling.adapter.in.web;

import com.relayops.scheduling.adapter.out.persistence.SchedulingCalendarRepository;
import com.relayops.scheduling.application.plan.ScheduleSpecCodec;
import com.relayops.scheduling.application.task.SchedulingAdminService;
import com.relayops.scheduling.domain.TaskDefinition;
import com.relayops.shared.web.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin API of the scheduling module. */
@RestController
@RequestMapping("/api/v1/scheduling")
public class SchedulingController {

    private final SchedulingAdminService service;
    private final ScheduleSpecCodec codec;

    public SchedulingController(SchedulingAdminService service, ScheduleSpecCodec codec) {
        this.service = service;
        this.codec = codec;
    }

    @GetMapping("/tasks")
    public ApiResponse<List<ScheduledTaskResponse>> list() {
        return ApiResponse.of(service.list().stream().map(this::toResponse).toList());
    }

    @GetMapping("/tasks/{id}")
    public ApiResponse<ScheduledTaskResponse> get(@PathVariable long id) {
        return ApiResponse.of(toResponse(service.get(id)));
    }

    @PostMapping("/tasks")
    public ApiResponse<ScheduledTaskResponse> create(@Valid @RequestBody ScheduledTaskRequest request) {
        return ApiResponse.of(toResponse(service.create(request.toCommand())));
    }

    @PutMapping("/tasks/{id}")
    public ApiResponse<ScheduledTaskResponse> update(@PathVariable long id,
                                                     @Valid @RequestBody ScheduledTaskRequest request) {
        return ApiResponse.of(toResponse(service.update(id, request.toCommand())));
    }

    @DeleteMapping("/tasks/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.of(null);
    }

    @PostMapping("/tasks/{id}/toggle")
    public ApiResponse<ScheduledTaskResponse> toggle(@PathVariable long id, @RequestParam boolean enabled) {
        return ApiResponse.of(toResponse(service.toggle(id, enabled)));
    }

    @PostMapping("/tasks/{id}/run")
    public ApiResponse<Void> run(@PathVariable long id) {
        service.runNow(id);
        return ApiResponse.of(null);
    }

    @GetMapping("/tasks/{id}/runs")
    public ApiResponse<List<SchedulingViews.TaskRunResponse>> runs(
            @PathVariable long id,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.of(service.runs(id, limit).stream()
                .map(SchedulingViews.TaskRunResponse::of)
                .toList());
    }

    @PostMapping("/spec/preview")
    public ApiResponse<List<SchedulingAdminService.PreviewEntry>> preview(
            @Valid @RequestBody PreviewRequest request) {
        return ApiResponse.of(service.preview(request.spec(), request.count()));
    }

    @GetMapping("/types")
    public ApiResponse<List<SchedulingViews.TaskTypeResponse>> types() {
        return ApiResponse.of(service.handlers().stream()
                .map(SchedulingViews.TaskTypeResponse::of)
                .toList());
    }

    @GetMapping("/calendars")
    public ApiResponse<List<SchedulingAdminService.CalendarView>> calendars() {
        return ApiResponse.of(service.calendars());
    }

    @GetMapping("/calendars/{code}/days")
    public ApiResponse<List<SchedulingCalendarRepository.CalendarDay>> days(
            @PathVariable String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(service.calendarDays(code, from, to));
    }

    @PutMapping("/calendars/{code}/days")
    public ApiResponse<List<SchedulingCalendarRepository.CalendarDay>> upsertDays(
            @PathVariable String code,
            @Valid @RequestBody CalendarDaysRequest request) {
        return ApiResponse.of(service.upsertDays(code, request.toCommands(), "MANUAL"));
    }

    @PostMapping("/calendars/{code}/import")
    public ApiResponse<List<SchedulingCalendarRepository.CalendarDay>> importDays(
            @PathVariable String code,
            @Valid @RequestBody CalendarDaysRequest request) {
        return ApiResponse.of(service.upsertDays(code, request.toCommands(), "IMPORT"));
    }

    /**
     * Imports calendar data from JSON / ICS / plain text. Format {@code AUTO} sniffs the content.
     */
    @PostMapping("/calendars/{code}/import/raw")
    public ApiResponse<List<SchedulingCalendarRepository.CalendarDay>> importRaw(
            @PathVariable String code,
            @Valid @RequestBody RawImportRequest request) {
        return ApiResponse.of(service.importRaw(code, request.content(), request.format()));
    }

    @DeleteMapping("/calendars/{code}/days/{day}")
    public ApiResponse<Void> deleteDay(
            @PathVariable String code,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate day) {
        service.deleteDay(code, day);
        return ApiResponse.of(null);
    }

    @PostMapping("/resync")
    public ApiResponse<Void> resync() {
        service.resync();
        return ApiResponse.of(null);
    }

    private ScheduledTaskResponse toResponse(TaskDefinition task) {
        return ScheduledTaskResponse.of(task, codec.write(task.spec()));
    }

    /** Bulk calendar day edit payload. */
    public record CalendarDaysRequest(@NotEmpty(message = "days 不能为空") List<CalendarDayRequest> days) {

        public List<SchedulingAdminService.CalendarDayCommand> toCommands() {
            return days.stream().map(CalendarDayRequest::toCommand).toList();
        }
    }

    /**
     * Raw calendar import payload.
     *
     * @param content JSON array / holiday-cn JSON / ICS / plain text lines
     * @param format  {@code AUTO} (default), {@code JSON}, {@code ICS} or {@code TEXT}
     */
    public record RawImportRequest(@NotBlank(message = "content 不能为空") String content, String format) {
    }

    /** One calendar day edit. */
    public record CalendarDayRequest(
            @NotNull(message = "day 不能为空") LocalDate day,
            @NotBlank(message = "dayType 不能为空") String dayType,
            String name) {

        public SchedulingAdminService.CalendarDayCommand toCommand() {
            return new SchedulingAdminService.CalendarDayCommand(day, dayType, name);
        }
    }
}
