package com.relayops.notification.application.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.notification.application.alert.AlertRules.AlertType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SilenceRuleTest {

    private static final Instant NOW = Instant.parse("2026-11-11T01:00:00Z");

    private static AlertStore.SilenceRule rule(String scopeType, String scopeKey, String alertType,
                                               Instant until) {
        return new AlertStore.SilenceRule(1L, scopeType, scopeKey, alertType, until, "维护窗口");
    }

    @Test
    void globalSilenceCoversEveryAccountAndType() {
        var silence = rule("GLOBAL", "*", null, NOW.plusSeconds(3600));

        assertThat(silence.matches(7L, AlertType.BALANCE_LOW, NOW)).isTrue();
        assertThat(silence.matches(99L, AlertType.COLLECT_STALE, NOW)).isTrue();
    }

    @Test
    void accountSilenceOnlyCoversThatAccount() {
        var silence = rule("ACCOUNT", "7", null, NOW.plusSeconds(3600));

        assertThat(silence.matches(7L, AlertType.COLLECT_FAILED, NOW)).isTrue();
        assertThat(silence.matches(8L, AlertType.COLLECT_FAILED, NOW)).isFalse();
    }

    @Test
    void typeScopedSilenceLeavesOtherTypesAlone() {
        var silence = rule("GLOBAL", "*", "BALANCE_LOW", NOW.plusSeconds(3600));

        assertThat(silence.matches(7L, AlertType.BALANCE_LOW, NOW)).isTrue();
        assertThat(silence.matches(7L, AlertType.COLLECT_FAILED, NOW)).isFalse();
    }

    @Test
    void expiredSilenceDoesNotApply() {
        var silence = rule("GLOBAL", "*", null, NOW.minusSeconds(60));

        assertThat(silence.matches(7L, AlertType.BALANCE_LOW, NOW)).isFalse();
    }
}
