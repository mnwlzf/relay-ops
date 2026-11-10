package com.relayops.upstream.domain;

/** Outcome of one collection attempt. */
public enum CollectStatus {

    SUCCESS,

    /** Some fields came back, others did not; treat as usable but worth noticing. */
    PARTIAL,

    FAILED,

    /** Account or platform disabled. */
    SKIPPED
}
