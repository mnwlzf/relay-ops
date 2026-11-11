package com.relayops.integration.onebot;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-target send throttle.
 *
 * <p>Throttling globally would make one busy group delay another; keeping the last-send time per
 * target lets each group/private chat have its own gap while the QQ account still respects the
 * overall send rate.</p>
 */
public final class SendThrottle {

    private final Duration interval;
    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();

    public SendThrottle(Duration interval) {
        this.interval = interval == null ? Duration.ZERO : interval;
    }

    /** @return milliseconds to wait before the next send to this target, 0 when no wait is needed */
    public long waitMillis(String target, long now) {
        if (interval.isZero() || interval.isNegative()) {
            return 0;
        }
        Long last = lastSentAt.get(target);
        if (last == null) {
            return 0;
        }
        long earliest = last + interval.toMillis();
        return Math.max(0, earliest - now);
    }

    public void markSent(String target, long now) {
        lastSentAt.put(target, now);
    }

    /** Blocks the calling thread for the required gap; interrupts are restored, never swallowed. */
    public void await(String target) {
        long wait = waitMillis(target, System.currentTimeMillis());
        if (wait <= 0) {
            markSent(target, System.currentTimeMillis());
            return;
        }
        try {
            Thread.sleep(wait);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        markSent(target, System.currentTimeMillis());
    }
}
