package com.relayops.scheduling.domain.day;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.scheduling.domain.DayFilter;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DayMatchersTest {

    /** 2026 National Day holiday plus a make-up workday on Saturday 2026-10-10. */
    private static final CalendarData CALENDAR = new CalendarData("cn-test", holiday());

    private static Map<LocalDate, DayType> holiday() {
        Map<LocalDate, DayType> overrides = new java.util.HashMap<>();
        for (LocalDate day = LocalDate.parse("2026-10-01"); day.isBefore(LocalDate.parse("2026-10-08"));
             day = day.plusDays(1)) {
            overrides.put(day, DayType.HOLIDAY);
        }
        overrides.put(LocalDate.parse("2026-10-10"), DayType.MAKEUP_WORKDAY);
        return overrides;
    }

    @Test
    void holidayIsNotAWorkdayAndMakeUpSaturdayIs() {
        DayMatcher workday = DayMatchers.from(DayFilter.workday());

        assertThat(workday.matches(LocalDate.parse("2026-10-01"), CALENDAR)).isFalse();
        assertThat(workday.matches(LocalDate.parse("2026-10-10"), CALENDAR)).isTrue();
        assertThat(CALENDAR.typeOf(LocalDate.parse("2026-10-10"))).isEqualTo(DayType.MAKEUP_WORKDAY);
        assertThat(CALENDAR.typeOf(LocalDate.parse("2026-10-11"))).isEqualTo(DayType.WEEKEND);
    }

    @Test
    void nonWorkdayIsTheComplement() {
        DayMatcher rest = DayMatchers.from(DayFilter.of(
                com.relayops.scheduling.domain.DayFilterType.NON_WORKDAY, List.of(), null, List.of()));

        assertThat(rest.matches(LocalDate.parse("2026-10-03"), CALENDAR)).isTrue();
        assertThat(rest.matches(LocalDate.parse("2026-10-10"), CALENDAR)).isFalse();
        assertThat(rest.matches(LocalDate.parse("2026-10-11"), CALENDAR)).isTrue();
    }

    @Test
    void weekdaysIgnoreTheCalendar() {
        DayMatcher weekend = DayMatchers.from(DayFilter.of(
                com.relayops.scheduling.domain.DayFilterType.WEEKDAYS,
                List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), null, List.of()));

        assertThat(weekend.matches(LocalDate.parse("2026-10-10"), CALENDAR)).isTrue();
        assertThat(weekend.matches(LocalDate.parse("2026-10-08"), CALENDAR)).isFalse();
    }

    @Test
    void nthWorkdayCountsOnlyWorkdays() {
        // 2026-09-01 is a Tuesday; the first workday of September is the 1st.
        assertThat(DayMatchers.isNthWorkday(LocalDate.parse("2026-09-01"), CALENDAR, 1)).isTrue();
        assertThat(DayMatchers.isNthWorkday(LocalDate.parse("2026-09-02"), CALENDAR, 1)).isFalse();
        assertThat(DayMatchers.isNthWorkday(LocalDate.parse("2026-09-02"), CALENDAR, 2)).isTrue();
    }

    @Test
    void lastWorkdaySkipsTrailingWeekend() {
        // 2026-09-30 is a Wednesday, so it is the last workday of September.
        assertThat(DayMatchers.isLastWorkday(LocalDate.parse("2026-09-30"), CALENDAR)).isTrue();
        // 2026-10-09 is a Friday, but 10-10 is a make-up workday, so 10-09 is not the last.
        assertThat(DayMatchers.isLastWorkday(LocalDate.parse("2026-10-09"), CALENDAR)).isFalse();
        // October 2026 ends on a Saturday, so the last workday is Friday the 30th.
        assertThat(DayMatchers.isLastWorkday(LocalDate.parse("2026-10-30"), CALENDAR)).isTrue();
        assertThat(DayMatchers.isLastWorkday(LocalDate.parse("2026-10-29"), CALENDAR)).isFalse();
    }

    @Test
    void monthDaysMatchTheDayOfMonth() {
        DayMatcher firstAndFifteenth = DayMatchers.from(DayFilter.of(
                com.relayops.scheduling.domain.DayFilterType.MONTH_DAYS, List.of(), List.of(1, 15), null, List.of()));

        assertThat(firstAndFifteenth.matches(LocalDate.parse("2026-10-01"), CALENDAR)).isTrue();
        assertThat(firstAndFifteenth.matches(LocalDate.parse("2026-10-15"), CALENDAR)).isTrue();
        assertThat(firstAndFifteenth.matches(LocalDate.parse("2026-10-02"), CALENDAR)).isFalse();
    }

    @Test
    void customDatesOnlyMatchListedDays() {
        DayMatcher custom = DayMatchers.from(DayFilter.of(
                com.relayops.scheduling.domain.DayFilterType.CUSTOM_DATES, List.of(), null,
                List.of(LocalDate.parse("2027-01-01"), LocalDate.parse("2027-02-14"))));

        assertThat(custom.matches(LocalDate.parse("2027-01-01"), CALENDAR)).isTrue();
        assertThat(custom.matches(LocalDate.parse("2027-01-02"), CALENDAR)).isFalse();
        assertThat(Set.of(DayOfWeek.MONDAY)).hasSize(1);
    }
}
