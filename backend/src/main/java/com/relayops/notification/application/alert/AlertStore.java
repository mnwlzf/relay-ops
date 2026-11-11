package com.relayops.notification.application.alert;

import com.relayops.notification.application.alert.AlertRules.AlertType;
import java.time.Instant;
import java.util.Map;

/** Alert delivery state, history and silence rules. */
public interface AlertStore {

    /** Key: {@code accountId:ALERT_TYPE}. */
    Map<String, Instant> lastSent();

    void record(long accountId, String alertType, Instant at, String value);

    void saveHistory(long accountId, String accountName, String alertType, String message,
                     String channels, Instant at);

    java.util.List<AlertHistoryRow> history(int limit);

    java.util.List<SilenceRule> silences();

    /** Only rules whose {@code until} is still in the future. */
    java.util.List<SilenceRule> activeSilences(Instant now);

    long addSilence(String scopeType, String scopeKey, String alertType, Instant until, String reason);

    void deleteSilence(long id);

    /** One delivered alert. */
    record AlertHistoryRow(long id, long accountId, String accountName, String alertType,
                           String message, String channels, Instant createdAt) {
    }

    /**
     * One silence rule.
     *
     * @param scopeType {@code GLOBAL} or {@code ACCOUNT}
     * @param scopeKey  {@code *} for global, otherwise the account id
     * @param alertType {@code null} means every type
     */
    record SilenceRule(long id, String scopeType, String scopeKey, String alertType,
                       Instant until, String reason) {

        public boolean matches(long accountId, AlertType type, Instant now) {
            if (until == null || !until.isAfter(now)) {
                return false;
            }
            boolean scopeOk = "GLOBAL".equals(scopeType)
                    || ("ACCOUNT".equals(scopeType) && scopeKey.equals(String.valueOf(accountId)));
            boolean typeOk = alertType == null || alertType.isBlank() || alertType.equals(type.name());
            return scopeOk && typeOk;
        }
    }
}
