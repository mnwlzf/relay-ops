package com.relayops.scheduling.config;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Scheduling module settings.
 *
 * @param enabled          whether the scheduler bootstraps and registers tasks
 * @param zone             default zone for schedules that do not declare one
 * @param defaultCalendar  calendar code used when a spec omits it
 * @param executionTimeout fallback timeout, overridden by the task's own value
 * @param silentAlertGrace how long a task may overrun its next fire time before it is considered silent
 * @param previewCount     how many future fire times the preview returns
 * @param shiftHorizonDays how far ahead holiday compensation is materialized (max 30)
 */
@ConfigurationProperties(prefix = "relay-ops.scheduling")
public record SchedulingProperties(
        Boolean enabled,
        ZoneId zone,
        String defaultCalendar,
        Duration executionTimeout,
        Duration silentAlertGrace,
        int previewCount,
        int shiftHorizonDays
) {

    public SchedulingProperties {
        zone = zone == null ? com.relayops.shared.time.BusinessTime.DEFAULT_ZONE : zone;
        defaultCalendar = defaultCalendar == null || defaultCalendar.isBlank() ? "cn-official" : defaultCalendar;
        executionTimeout = executionTimeout == null ? Duration.ofMinutes(5) : executionTimeout;
        silentAlertGrace = silentAlertGrace == null ? Duration.ofMinutes(15) : silentAlertGrace;
        previewCount = previewCount <= 0 ? 10 : Math.min(previewCount, 100);
        shiftHorizonDays = shiftHorizonDays <= 0 ? 30 : Math.min(shiftHorizonDays, 30);
    }

    public boolean schedulingEnabled() {
        return enabled == null || enabled;
    }
}
