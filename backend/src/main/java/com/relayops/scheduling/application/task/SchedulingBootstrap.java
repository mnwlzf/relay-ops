package com.relayops.scheduling.application.task;

import com.relayops.scheduling.adapter.out.persistence.ScheduledTaskRunRepository;
import com.relayops.scheduling.application.calendar.SchedulingCalendarRegistry;
import com.relayops.scheduling.config.SchedulingProperties;
import com.relayops.shared.time.BusinessTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup reconciliation: close runs left behind by a crash, then align Quartz with the database
 * and re-register workday calendars (Quartz does not persist calendar objects).
 */
@Component
public class SchedulingBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchedulingBootstrap.class);

    private final SchedulingProperties properties;
    private final ScheduledTaskRunRepository runs;
    private final TaskRegistrationService registration;
    private final SchedulingCalendarRegistry calendars;
    private final BusinessTime time;

    public SchedulingBootstrap(SchedulingProperties properties,
                               ScheduledTaskRunRepository runs,
                               TaskRegistrationService registration,
                               SchedulingCalendarRegistry calendars,
                               BusinessTime time) {
        this.properties = properties;
        this.runs = runs;
        this.registration = registration;
        this.calendars = calendars;
        this.time = time;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.schedulingEnabled()) {
            log.info("调度模块已禁用（relay-ops.scheduling.enabled=false），跳过对账");
            return;
        }
        runs.failStaleRuns(time.now(), "应用重启，上次执行未结束");
        registration.resync();
        log.info("调度启动对账完成: calendars={}", calendars.size());
    }
}
