package com.relayops.scheduling.application.plan;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.scheduling.domain.ScheduleSpec;
import com.relayops.scheduling.domain.day.CalendarData;
import com.relayops.scheduling.domain.day.DayType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Preview must mirror what Quartz will actually do: this is the contract the admin page relies on.
 */
class FireTimeCalculatorTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper json = new ObjectMapper();
    private final ScheduleSpecCodec codec = new ScheduleSpecCodec(json);
    private final ScheduleSpecCompiler compiler = new ScheduleSpecCompiler(codec);

    private static final CalendarData CALENDAR = nationalDay2026();

    private static CalendarData nationalDay2026() {
        Map<LocalDate, DayType> overrides = new HashMap<>();
        for (LocalDate day = LocalDate.parse("2026-10-01"); day.isBefore(LocalDate.parse("2026-10-08"));
             day = day.plusDays(1)) {
            overrides.put(day, DayType.HOLIDAY);
        }
        overrides.put(LocalDate.parse("2026-10-10"), DayType.MAKEUP_WORKDAY);
        return new CalendarData("cn-test", overrides);
    }

    private CompiledSchedule compile(String body) {
        try {
            ScheduleSpec spec = codec.parse(json.readTree(body));
            return compiler.compile(spec);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Instant at(int month, int day, int hour) {
        return at(month, day, hour, 0);
    }

    private static Instant at(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, ZONE).toInstant();
    }

    @Test
    void workdayNineAmSkipsTheHolidayAndKeepsTheMakeUpSaturday() {
        CompiledSchedule schedule = compile("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "WORKDAY" },
                  "times": ["09:00"]
                }
                """);

        List<Instant> fires = FireTimeCalculator.nextFireTimes(
                schedule, CALENDAR, at(9, 30, 12), 5);

        assertThat(fires).containsExactly(
                at(10, 8, 9),   // first workday after the holiday
                at(10, 9, 9),
                at(10, 10, 9),  // make-up workday (Saturday)
                at(10, 12, 9),
                at(10, 13, 9));
    }

    @Test
    void forcePolicyIgnoresTheCalendar() {
        CompiledSchedule schedule = compile("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "WORKDAY" },
                  "times": ["09:00"],
                  "holidayPolicy": "FORCE"
                }
                """);

        List<Instant> fires = FireTimeCalculator.nextFireTimes(
                schedule, CALENDAR, at(9, 30, 12), 3);

        assertThat(fires).containsExactly(at(10, 1, 9), at(10, 2, 9), at(10, 3, 9));
    }

    @Test
    void multipleTimesPerDayAreMergedInOrder() {
        CompiledSchedule schedule = compile("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "WORKDAY" },
                  "times": ["09:00", "18:30"]
                }
                """);

        List<Instant> fires = FireTimeCalculator.nextFireTimes(
                schedule, CALENDAR, at(10, 8, 8), 4);

        assertThat(fires).containsExactly(
                at(10, 8, 9), at(10, 8, 18, 30), at(10, 9, 9), at(10, 9, 18, 30));
    }

    @Test
    void firstWorkdayOfMonthSkipsTheWeekend() {
        CompiledSchedule schedule = compile("""
                {
                  "kind": "MONTH_NTH_WORKDAY",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "MONTH_NTH_WORKDAY", "nth": 1 },
                  "times": ["09:00"]
                }
                """);

        List<Instant> fires = FireTimeCalculator.nextFireTimes(
                schedule, CALENDAR, at(7, 15, 0), 2);

        // 2026-08-01 is a Saturday, 08-02 a Sunday, so the first workday is 08-03.
        assertThat(fires).containsExactly(at(8, 3, 9), at(9, 1, 9));
    }

    @Test
    void lastWorkdayOfMonthIsComputedFromTheCalendar() {
        CompiledSchedule schedule = compile("""
                {
                  "kind": "MONTH_NTH_WORKDAY",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "MONTH_LAST_WORKDAY" },
                  "times": ["18:00"]
                }
                """);

        List<Instant> fires = FireTimeCalculator.nextFireTimes(
                schedule, CALENDAR, at(9, 1, 0), 2);

        assertThat(fires).containsExactly(at(9, 30, 18), at(10, 30, 18));
    }

    @Test
    void intervalProducesEvenlySpacedFires() {
        CompiledSchedule schedule = compile("""
                { "kind": "INTERVAL", "interval": "PT10M" }
                """);

        Instant start = at(10, 8, 9);
        List<Instant> fires = FireTimeCalculator.nextFireTimes(schedule, CALENDAR, start, 3);

        assertThat(fires).containsExactly(
                start.plusSeconds(600), start.plusSeconds(1200), start.plusSeconds(1800));
    }

    @Test
    void onceReturnsTheInstantOnlyWhileItIsInTheFuture() {
        CompiledSchedule schedule = compile("""
                { "kind": "ONCE", "once": "2026-12-01T10:00:00+08:00" }
                """);

        assertThat(FireTimeCalculator.nextFireTimes(schedule, CALENDAR, at(11, 1, 0), 1))
                .containsExactly(at(12, 1, 10));
        assertThat(FireTimeCalculator.nextFireTimes(schedule, CALENDAR, at(12, 2, 0), 1)).isEmpty();
    }

    @Test
    void customDatesFireOnlyOnListedDays() {
        CompiledSchedule schedule = compile("""
                {
                  "kind": "CALENDAR_TIMES",
                  "dayFilter": { "type": "CUSTOM_DATES", "dates": ["2026-12-31", "2027-01-04"] },
                  "times": ["10:00"]
                }
                """);

        List<Instant> fires = FireTimeCalculator.nextFireTimes(
                schedule, CalendarData.empty("none"), at(12, 1, 0), 5);

        assertThat(fires).containsExactly(at(12, 31, 10),
                ZonedDateTime.of(2027, 1, 4, 10, 0, 0, 0, ZONE).toInstant());
    }

    @Test
    void shiftPolicyRemovesTheHolidayMondayFromThePreview() {
        CompiledSchedule schedule = compile("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "WEEKDAYS", "weekdays": ["MON"] },
                  "times": ["09:00"],
                  "holidayPolicy": "NEXT_WORKDAY"
                }
                """);

        List<Instant> fires = FireTimeCalculator.nextFireTimes(
                schedule, CALENDAR, at(9, 30, 12), 3);

        // 10-05 is a Monday inside the holiday; with a shift policy it must not fire on its own.
        assertThat(fires).containsExactly(at(10, 12, 9), at(10, 19, 9), at(10, 26, 9));
    }

    @Test
    void cronPreviewIsIndependentOfTheCalendar() {
        CompiledSchedule schedule = compile("""
                { "kind": "CRON", "cron": "0 0 9 * * ?" }
                """);

        List<Instant> fires = FireTimeCalculator.nextFireTimes(
                schedule, CALENDAR, at(10, 1, 12), 2);

        assertThat(fires).containsExactly(at(10, 2, 9), at(10, 3, 9));
    }
}
