package com.relayops.scheduling.domain;

import com.relayops.shared.error.BusinessException;
import java.time.LocalTime;

/**
 * Quiet hours during which broadcast-like tasks must not fire.
 *
 * <p>A window may wrap midnight, e.g. {@code 23:00 - 07:00}.</p>
 */
public record QuietHours(LocalTime from, LocalTime to) {

    public QuietHours {
        if (from == null || to == null) {
            throw invalid("静默时段需要 from/to");
        }
        if (from.equals(to)) {
            throw invalid("静默时段的 from/to 不能相同");
        }
    }

    public static QuietHours of(LocalTime from, LocalTime to) {
        return new QuietHours(from, to);
    }

    /** {@code true} when the given local time falls inside the window. */
    public boolean contains(LocalTime time) {
        if (from.isBefore(to)) {
            return !time.isBefore(from) && time.isBefore(to);
        }
        return !time.isBefore(from) || time.isBefore(to);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("SCHEDULE_SPEC_INVALID", message);
    }
}
