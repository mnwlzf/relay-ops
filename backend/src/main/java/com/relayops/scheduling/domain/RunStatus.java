package com.relayops.scheduling.domain;

/** Final state of one execution. */
public enum RunStatus {

    RUNNING,
    SUCCESS,
    FAILED,
    TIMEOUT,
    SKIPPED
}
