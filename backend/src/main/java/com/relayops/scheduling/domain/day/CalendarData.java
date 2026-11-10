package com.relayops.scheduling.domain.day;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

/**
 * Work calendar data: only exceptions are stored, everything else falls back to the weekday rule.
 *
 * <p>Resolution order: explicit {@link DayType} override, then Monday-Friday = workday,
 * Saturday/Sunday = weekend. This is what makes 调休 work: a Saturday listed as
 * {@link DayType#MAKEUP_WORKDAY} is a workday, a Wednesday listed as {@link DayType#HOLIDAY} is not.</p>
 */
public record CalendarData(String code, Map<LocalDate, DayType> overrides) {

    public CalendarData {
        overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
    }

    public static CalendarData empty(String code) {
        return new CalendarData(code, Map.of());
    }

    public DayType typeOf(LocalDate date) {
        DayType override = overrides.get(date);
        if (override != null) {
            return override;
        }
        return defaultType(date);
    }

    public boolean isWorkday(LocalDate date) {
        return typeOf(date).isWorkday();
    }

    public int size() {
        return overrides.size();
    }

    private static DayType defaultType(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
                ? DayType.WEEKEND
                : DayType.WORKDAY;
    }
}
