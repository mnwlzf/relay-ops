package com.relayops.notification.application.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.notification.application.alert.AlertRules.AlertType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AlertRulesTest {

    private static final Instant NOW = Instant.parse("2026-11-10T02:00:00Z");
    private static final Duration COOLDOWN = Duration.ofHours(6);

    private static AlertRules.AccountState state(BigDecimal balance, String status, Instant collectedAt,
                                                 Map<AlertType, Instant> lastSent) {
        return new AlertRules.AccountState(1L, "主账号", balance, status, collectedAt, lastSent);
    }

    private static List<AlertRules.Alert> evaluate(AlertRules.AccountState state, Instant staleBefore) {
        return AlertRules.evaluate(state, new BigDecimal("10"), staleBefore, NOW, COOLDOWN);
    }

    @Test
    void healthyAccountProducesNothing() {
        assertThat(evaluate(state(new BigDecimal("100"), "SUCCESS", NOW.minusSeconds(600), Map.of()),
                NOW.minus(Duration.ofHours(2)))).isEmpty();
    }

    @Test
    void lowBalanceIsReported() {
        List<AlertRules.Alert> alerts = evaluate(
                state(new BigDecimal("3.5"), "SUCCESS", NOW.minusSeconds(600), Map.of()),
                NOW.minus(Duration.ofHours(2)));

        assertThat(alerts).extracting(AlertRules.Alert::type).containsExactly(AlertType.BALANCE_LOW);
        assertThat(alerts.get(0).message()).contains("3.5").contains("10");
    }

    @Test
    void failedCollectionAndSilenceAreSeparateAlerts() {
        List<AlertRules.Alert> alerts = evaluate(
                state(new BigDecimal("100"), "FAILED", NOW.minus(Duration.ofHours(5)), Map.of()),
                NOW.minus(Duration.ofHours(2)));

        assertThat(alerts).extracting(AlertRules.Alert::type)
                .containsExactly(AlertType.COLLECT_FAILED, AlertType.COLLECT_STALE);
    }

    @Test
    void neverCollectedCountsAsSilence() {
        List<AlertRules.Alert> alerts = evaluate(
                state(new BigDecimal("100"), null, null, Map.of()), NOW.minus(Duration.ofHours(2)));

        assertThat(alerts).extracting(AlertRules.Alert::type).containsExactly(AlertType.COLLECT_STALE);
        assertThat(alerts.get(0).message()).contains("从未");
    }

    @Test
    void cooldownSuppressesOnlyTheSameAlertType() {
        Map<AlertType, Instant> lastSent = new EnumMap<>(AlertType.class);
        lastSent.put(AlertType.BALANCE_LOW, NOW.minus(Duration.ofHours(1)));

        List<AlertRules.Alert> alerts = evaluate(
                state(new BigDecimal("1"), "FAILED", NOW.minus(Duration.ofHours(4)), lastSent),
                NOW.minus(Duration.ofHours(2)));

        assertThat(alerts).extracting(AlertRules.Alert::type)
                .containsExactly(AlertType.COLLECT_FAILED, AlertType.COLLECT_STALE);
    }

    @Test
    void cooldownExpires() {
        Map<AlertType, Instant> lastSent = new EnumMap<>(AlertType.class);
        lastSent.put(AlertType.BALANCE_LOW, NOW.minus(Duration.ofHours(7)));

        assertThat(evaluate(state(new BigDecimal("1"), "SUCCESS", NOW.minusSeconds(60), lastSent),
                NOW.minus(Duration.ofHours(2))))
                .extracting(AlertRules.Alert::type)
                .containsExactly(AlertType.BALANCE_LOW);
    }
}
