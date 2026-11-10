package com.relayops.scheduling.domain;

/** Behaviour when a previous execution of the same task is still running. */
public enum ConcurrencyPolicy {

    /** Skip this occurrence. */
    SKIP,

    /** Let the occurrence wait for the running execution (handled by Quartz). */
    QUEUE
}
