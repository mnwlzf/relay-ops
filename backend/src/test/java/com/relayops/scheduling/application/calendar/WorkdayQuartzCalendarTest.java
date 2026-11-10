package com.relayops.scheduling.application.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.scheduling.domain.DayFilter;
import com.relayops.scheduling.domain.day.CalendarData;
import com.relayops.scheduling.domain.day.DayMatchers;
import com.relayops.scheduling.domain.day.DayType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkdayQuartzCalendarTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static CalendarData calendar() {
        Map<LocalDate, DayType> overrides = new HashMap<>();
        for (LocalDate day = LocalDate.parse("2026-10-01"); day.isBefore(LocalDate.parse("2026-10-08"));
             day = day.plusDays(1)) {
            overrides.put(day, DayType.HOLIDAY);
        }
        overrides.put(LocalDate.parse("2026-10-10"), DayType.MAKEUP_WORKDAY);
        return new CalendarData("cn-test", overrides);
    }

    private static long millis(int month, int day, int hour) {
        return ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, ZONE).toInstant().toEpochMilli();
    }

    private final WorkdayQuartzCalendar workday = new WorkdayQuartzCalendar(
            DayMatchers.from(DayFilter.workday()), calendar(), ZONE, "test");

    @Test
    void includesMakeUpWorkdayAndExcludesHoliday() {
        assertThat(workday.isTimeIncluded(millis(10, 10, 9))).isTrue();
        assertThat(workday.isTimeIncluded(millis(10, 3, 9))).isFalse();
        assertThat(workday.isTimeIncluded(millis(10, 11, 9))).isFalse();
    }

    @Test
    void nextIncludedTimeJumpsOverTheHoliday() {
        long next = workday.getNextIncludedTime(millis(10, 1, 9));

        assertThat(next).isEqualTo(millis(10, 8, 0));
        assertThat(Instant.ofEpochMilli(next).atZone(ZONE).toLocalDate())
                .isEqualTo(LocalDate.parse("2026-10-08"));
    }

    @Test
    void cloneKeepsTheSameBehaviour() {
        WorkdayQuartzCalendar copy = (WorkdayQuartzCalendar) workday.clone();

        assertThat(copy.isTimeIncluded(millis(10, 10, 9))).isTrue();
        assertThat(copy.getDescription()).isEqualTo("test");
    }
}
