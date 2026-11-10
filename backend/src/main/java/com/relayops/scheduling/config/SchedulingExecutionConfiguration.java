package com.relayops.scheduling.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SchedulingExecutionConfiguration {

    /**
     * Executor used to bound handler runtime with a timeout.
     *
     * <p>Quartz worker threads stay free while a handler runs, and a timed-out handler can be
     * cancelled without blocking the scheduler.</p>
     */
    @Bean(destroyMethod = "close")
    ExecutorService schedulingTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
