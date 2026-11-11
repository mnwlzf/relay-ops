package com.relayops.integration.onebot;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SendThrottleTest {

    @Test
    void firstSendHasNoWait() {
        SendThrottle throttle = new SendThrottle(Duration.ofMillis(500));

        assertThat(throttle.waitMillis("group:1", 1000L)).isZero();
    }

    @Test
    void secondSendWaitsForTheGap() {
        SendThrottle throttle = new SendThrottle(Duration.ofMillis(500));
        throttle.markSent("group:1", 1000L);

        assertThat(throttle.waitMillis("group:1", 1200L)).isEqualTo(300);
        assertThat(throttle.waitMillis("group:1", 1600L)).isZero();
    }

    @Test
    void targetsAreThrottledIndependently() {
        SendThrottle throttle = new SendThrottle(Duration.ofMillis(500));
        throttle.markSent("group:1", 1000L);

        assertThat(throttle.waitMillis("group:2", 1100L)).isZero();
        assertThat(throttle.waitMillis("private:9", 1100L)).isZero();
    }

    @Test
    void zeroIntervalDisablesThrottling() {
        SendThrottle throttle = new SendThrottle(Duration.ZERO);
        throttle.markSent("group:1", 1000L);

        assertThat(throttle.waitMillis("group:1", 1000L)).isZero();
    }
}
