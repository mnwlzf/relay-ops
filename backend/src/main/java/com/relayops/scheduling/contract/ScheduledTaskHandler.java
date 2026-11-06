package com.relayops.scheduling.contract;

/**
 * SPI implemented by business modules that expose dynamically scheduled work.
 */
public interface ScheduledTaskHandler {

    String taskType();

    void execute(String configuredTaskName);
}
