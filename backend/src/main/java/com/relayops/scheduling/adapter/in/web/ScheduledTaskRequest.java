package com.relayops.scheduling.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import com.relayops.scheduling.application.task.TaskCommand;
import com.relayops.scheduling.domain.ConcurrencyPolicy;
import com.relayops.scheduling.domain.MisfirePolicy;
import com.relayops.scheduling.domain.QuietHours;
import com.relayops.shared.error.BusinessException;

/** Create/update payload of a scheduled task. */
public record ScheduledTaskRequest(
        @NotBlank(message = "任务名称不能为空") @Size(max = 120, message = "任务名称不能超过 120 字") String name,
        @NotBlank(message = "任务类型不能为空") String taskType,
        @NotNull(message = "必须提供 spec") JsonNode spec,
        JsonNode params,
        String misfirePolicy,
        Integer maxCatchUp,
        String concurrencyPolicy,
        Integer timeoutSeconds,
        Integer retryMax,
        Integer retryBackoffSeconds,
        List<String> quietHours,
        Boolean enabled
) {

    public TaskCommand toCommand() {
        return new TaskCommand(
                name,
                taskType,
                spec,
                params,
                parseMisfire(misfirePolicy),
                maxCatchUp == null ? 1 : maxCatchUp,
                parseConcurrency(concurrencyPolicy),
                timeoutSeconds == null ? 300 : timeoutSeconds,
                retryMax == null ? 0 : retryMax,
                retryBackoffSeconds == null ? 60 : retryBackoffSeconds,
                parseQuietHours(quietHours),
                enabled == null || enabled);
    }

    private MisfirePolicy parseMisfire(String value) {
        if (value == null || value.isBlank()) {
            return MisfirePolicy.FIRE_ONCE;
        }
        try {
            return MisfirePolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("SCHEDULING_TASK_INVALID", "未知的 misfirePolicy: " + value);
        }
    }

    private ConcurrencyPolicy parseConcurrency(String value) {
        if (value == null || value.isBlank()) {
            return ConcurrencyPolicy.SKIP;
        }
        try {
            return ConcurrencyPolicy.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("SCHEDULING_TASK_INVALID", "未知的 concurrencyPolicy: " + value);
        }
    }

    private QuietHours parseQuietHours(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() != 2) {
            throw new BusinessException("SCHEDULING_TASK_INVALID", "quietHours 需要两个时刻，例如 [\"23:00\",\"07:00\"]");
        }
        try {
            return QuietHours.of(LocalTime.parse(values.get(0)), LocalTime.parse(values.get(1)));
        } catch (RuntimeException ex) {
            throw new BusinessException("SCHEDULING_TASK_INVALID", "quietHours 时刻格式不合法");
        }
    }
}
