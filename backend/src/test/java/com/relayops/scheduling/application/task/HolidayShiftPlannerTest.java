package com.relayops.scheduling.application.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.scheduling.application.plan.ScheduleSpecCodec;
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

class HolidayShiftPlannerTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ObjectMapper json = new ObjectMapper();
    private final ScheduleSpecCodec codec = new ScheduleSpecCodec(json);

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

    private ScheduleSpec spec(String body) {
        try {
            return codec.parse(json.readTree(body));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Instant at(int month, int day, int hour) {
        return ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, ZONE).toInstant();
    }

    private static Instant at(int month, int day, int hour, int minute) {
        return ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, ZONE).toInstant();
    }

    @Test
    void weeklyMondayOnAHolidayFiresOnTheNextWorkday() {
        ScheduleSpec spec = spec("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "WEEKDAYS", "weekdays": ["MON"] },
                  "times": ["09:00"],
                  "holidayPolicy": "NEXT_WORKDAY"
                }
                """);

        List<HolidayShiftPlanner.ShiftPlan> plans = HolidayShiftPlanner.plan(
                spec, CALENDAR, at(9, 30, 0), 10);

        // 2026-10-05 is a Monday inside the holiday; the first workday after it is 10-08.
        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).plannedDate()).isEqualTo(LocalDate.parse("2026-10-05"));
        assertThat(plans.get(0).targetDate()).isEqualTo(LocalDate.parse("2026-10-08"));
        assertThat(plans.get(0).shiftedAt()).isEqualTo(at(10, 8, 9));
    }

    @Test
    void dailyWorkdayScheduleNeverProducesADuplicateShift() {
        ScheduleSpec spec = spec("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "WORKDAY" },
                  "times": ["09:00"],
                  "holidayPolicy": "NEXT_WORKDAY"
                }
                """);

        // Every blocked day maps onto 10-08, which already fires at 09:00 by the regular schedule.
        assertThat(HolidayShiftPlanner.plan(spec, CALENDAR, at(9, 30, 0), 10)).isEmpty();
    }

    @Test
    void previousWorkdayPolicyMovesTheFireEarlier() {
        ScheduleSpec spec = spec("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "WEEKDAYS", "weekdays": ["MON"] },
                  "times": ["09:00"],
                  "holidayPolicy": "PREV_WORKDAY"
                }
                """);

        List<HolidayShiftPlanner.ShiftPlan> plans = HolidayShiftPlanner.plan(
                spec, CALENDAR, at(9, 29, 0), 10);

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).targetDate()).isEqualTo(LocalDate.parse("2026-09-30"));
        assertThat(plans.get(0).shiftedAt()).isEqualTo(at(9, 30, 9));
    }

    @Test
    void severalBlockedDaysCollapseIntoOneShift() {
        ScheduleSpec spec = spec("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "WEEKDAYS", "weekdays": ["MON", "TUE"] },
                  "times": ["09:00"],
                  "holidayPolicy": "NEXT_WORKDAY"
                }
                """);

        List<HolidayShiftPlanner.ShiftPlan> plans = HolidayShiftPlanner.plan(
                spec, CALENDAR, at(9, 30, 0), 10);

        // Monday 10-05 and Tuesday 10-06 both resolve to 10-08 09:00 -> one fire.
        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).shiftedAt()).isEqualTo(at(10, 8, 9));
        assertThat(plans.get(0).plannedDate()).isEqualTo(LocalDate.parse("2026-10-05"));
    }

    @Test
    void customDateOnAHolidayShiftsForward() {
        ScheduleSpec spec = spec("""
                {
                  "kind": "CALENDAR_TIMES",
                  "dayFilter": { "type": "CUSTOM_DATES", "dates": ["2026-10-03"] },
                  "times": ["10:30"],
                  "holidayPolicy": "NEXT_WORKDAY"
                }
                """);

        List<HolidayShiftPlanner.ShiftPlan> plans = HolidayShiftPlanner.plan(
                spec, CALENDAR, at(9, 30, 0), 10);

        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).shiftedAt()).isEqualTo(at(10, 8, 10, 30));
    }

    @Test
    void monthlyFirstDayIsShiftedOffAWeekend() {
        ScheduleSpec spec = spec("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "MONTH_DAYS", "days": [1] },
                  "times": ["09:00"],
                  "holidayPolicy": "NEXT_WORKDAY"
                }
                """);

        List<HolidayShiftPlanner.ShiftPlan> plans = HolidayShiftPlanner.plan(
                spec, CALENDAR, at(10, 15, 0), 30);

        // 2026-11-01 is a Sunday, so the monthly report moves to Monday 11-02.
        assertThat(plans).hasSize(1);
        assertThat(plans.get(0).plannedDate()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(plans.get(0).shiftedAt()).isEqualTo(ZonedDateTime.of(2026, 11, 2, 9, 0, 0, 0, ZONE).toInstant());
    }

    @Test
    void skipAndForcePoliciesDoNotPlanAnything() {
        ScheduleSpec skip = spec("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "WORKDAY" },
                  "times": ["09:00"]
                }
                """);
        ScheduleSpec force = spec("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "WORKDAY" },
                  "times": ["09:00"],
                  "holidayPolicy": "FORCE"
                }
                """);

        assertThat(HolidayShiftPlanner.applies(skip)).isFalse();
        assertThat(HolidayShiftPlanner.applies(force)).isFalse();
        assertThat(HolidayShiftPlanner.plan(skip, CALENDAR, at(9, 30, 0), 10)).isEmpty();
    }

    @Test
    void pastShiftsAreNotScheduled() {
        ScheduleSpec spec = spec("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-test",
                  "dayFilter": { "type": "WEEKDAYS", "weekdays": ["MON"] },
                  "times": ["09:00"],
                  "holidayPolicy": "NEXT_WORKDAY"
                }
                """);

        // Scanning after the shift target has passed must not reschedule it.
        assertThat(HolidayShiftPlanner.plan(spec, CALENDAR, at(10, 9, 0), 10)).isEmpty();
    }
}
