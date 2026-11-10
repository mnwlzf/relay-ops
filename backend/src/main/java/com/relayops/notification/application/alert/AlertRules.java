package com.relayops.notification.application.alert;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pure alert decisions.
 *
 * <p>Three reasons only: balance below threshold, last collection failed, or collection went silent.
 * Cooldown is per alert type, which is what keeps a broken account from mailing every ten minutes.</p>
 */
public final class AlertRules {

    private AlertRules() {
    }

    public enum AlertType {
        BALANCE_LOW,
        COLLECT_FAILED,
        COLLECT_STALE
    }

    /**
     * One account's alert-relevant state.
     *
     * @param lastSent per-type delivery time, empty when never sent
     */
    public record AccountState(long accountId, String accountName, BigDecimal balance,
                               String lastStatus, Instant lastCollectedAt,
                               Map<AlertType, Instant> lastSent) {

        public AccountState {
            lastSent = lastSent == null ? Map.of() : Map.copyOf(lastSent);
        }
    }

    /** One alert to deliver. */
    public record Alert(long accountId, String accountName, AlertType type, String message) {
    }

    public static List<Alert> evaluate(AccountState account, BigDecimal balanceThreshold,
                                       Instant staleBefore, Instant now, Duration cooldown) {
        List<Alert> alerts = new ArrayList<>(3);

        if (balanceThreshold != null && balanceThreshold.signum() >= 0
                && account.balance() != null
                && account.balance().compareTo(balanceThreshold) < 0) {
            add(alerts, account, AlertType.BALANCE_LOW, now, cooldown,
                    "余额 " + account.balance().toPlainString() + " 低于阈值 "
                            + balanceThreshold.toPlainString());
        }
        if ("FAILED".equals(account.lastStatus())) {
            add(alerts, account, AlertType.COLLECT_FAILED, now, cooldown, "最近一次采集失败");
        }
        if (account.lastCollectedAt() == null) {
            add(alerts, account, AlertType.COLLECT_STALE, now, cooldown, "从未成功采集");
        } else if (account.lastCollectedAt().isBefore(staleBefore)) {
            add(alerts, account, AlertType.COLLECT_STALE, now, cooldown,
                    "超过预期时间未采集（最近 " + account.lastCollectedAt() + "）");
        }
        return List.copyOf(alerts);
    }

    private static void add(List<Alert> alerts, AccountState account, AlertType type,
                            Instant now, Duration cooldown, String message) {
        if (suppressed(account.lastSent().get(type), now, cooldown)) {
            return;
        }
        alerts.add(new Alert(account.accountId(), account.accountName(), type, message));
    }

    static boolean suppressed(Instant lastSentAt, Instant now, Duration cooldown) {
        return lastSentAt != null && cooldown != null && lastSentAt.isAfter(now.minus(cooldown));
    }
}
