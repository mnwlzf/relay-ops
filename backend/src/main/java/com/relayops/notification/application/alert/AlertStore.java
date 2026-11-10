package com.relayops.notification.application.alert;

import java.time.Instant;
import java.util.Map;

/** Alert delivery state, used for cooldown. */
public interface AlertStore {

    /** Key: {@code accountId:ALERT_TYPE}. */
    Map<String, Instant> lastSent();

    void record(long accountId, String alertType, Instant at, String value);
}
