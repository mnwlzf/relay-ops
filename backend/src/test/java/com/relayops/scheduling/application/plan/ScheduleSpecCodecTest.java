package com.relayops.scheduling.application.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.scheduling.domain.DayFilterType;
import com.relayops.scheduling.domain.HolidayPolicy;
import com.relayops.scheduling.domain.ScheduleKind;
import com.relayops.scheduling.domain.ScheduleSpec;
import com.relayops.shared.error.BusinessException;
import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ScheduleSpecCodecTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ScheduleSpecCodec codec = new ScheduleSpecCodec(json);

    private ScheduleSpec parse(String body) {
        try {
            return codec.parse(json.readTree(body));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    void parsesWorkdayTimesWithDefaults() {
        ScheduleSpec spec = parse("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-official",
                  "dayFilter": { "type": "WORKDAY" },
                  "times": ["18:30", "09:00"]
                }
                """);

        assertThat(spec.kind()).isEqualTo(ScheduleKind.CALENDAR_TIMES);
        assertThat(spec.zone().getId()).isEqualTo("Asia/Shanghai");
        assertThat(spec.times()).containsExactly(LocalTime.of(9, 0), LocalTime.of(18, 30));
        assertThat(spec.holidayPolicy()).isEqualTo(HolidayPolicy.SKIP);
        assertThat(spec.usesDayMatcher()).isTrue();
        assertThat(spec.needsCalendarData()).isTrue();
    }

    @Test
    void keepsCronSchedulesCalendarFree() {
        ScheduleSpec spec = parse("""
                { "kind": "CRON", "cron": "0 */10 * * * ?" }
                """);

        assertThat(spec.kind()).isEqualTo(ScheduleKind.CRON);
        assertThat(spec.usesDayMatcher()).isFalse();
        assertThat(spec.times()).isEmpty();
    }

    @Test
    void monthDaysIsADatePatternWithoutCalendarData() {
        ScheduleSpec spec = parse("""
                {
                  "kind": "CALENDAR_TIMES",
                  "dayFilter": { "type": "MONTH_DAYS", "days": [1, 15] },
                  "times": ["09:00"],
                  "holidayPolicy": "NEXT_WORKDAY"
                }
                """);

        assertThat(spec.dayFilter().daysOfMonth()).containsExactly(1, 15);
        // The matcher must still be attached (otherwise Quartz fires daily), but no calendar data is needed.
        assertThat(spec.usesDayMatcher()).isTrue();
        assertThat(spec.needsCalendarData()).isFalse();
        assertThat(spec.shiftsToWorkday()).isTrue();
    }

    @Test
    void rejectsUnknownKind() {
        assertThatThrownBy(() -> parse("""
                { "kind": "EVERY_FULL_MOON" }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未知的 spec.kind");
    }

    @Test
    void rejectsOutOfRangeCronEvenWhenQuartzWouldAcceptIt() {
        // Quartz parses "0 0 99 * * ?" happily and then never fires; save time must reject it.
        assertThatThrownBy(() -> parse("""
                { "kind": "CRON", "cron": "0 0 99 * * ?" }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出范围");
    }

    @Test
    void rejectsMalformedCron() {
        assertThatThrownBy(() -> parse("""
                { "kind": "CRON", "cron": "not-a-cron" }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cron 表达式不合法");
    }

    @Test
    void acceptsNightlyMaintenanceCron() {
        ScheduleSpec spec = parse("""
                { "kind": "CRON", "cron": "0 30 0 * * ?" }
                """);

        assertThat(spec.cron()).isEqualTo("0 30 0 * * ?");
    }

    @Test
    void requiresCalendarForWorkdayFilter() {
        assertThatThrownBy(() -> parse("""
                { "kind": "CALENDAR_TIMES", "dayFilter": { "type": "WORKDAY" }, "times": ["09:00"] }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("calendarCode");
    }

    @Test
    void rejectsIntervalWithoutDuration() {
        assertThatThrownBy(() -> parse("""
                { "kind": "INTERVAL" }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("interval");
    }

    @Test
    void rejectsTimeOutsideActiveWindow() {
        assertThatThrownBy(() -> parse("""
                {
                  "kind": "CALENDAR_TIMES",
                  "dayFilter": { "type": "ALL" },
                  "times": ["23:30"],
                  "activeWindow": { "from": "08:00", "to": "22:00" }
                }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("activeWindow");
    }

    @Test
    void monthNthWorkdayRequiresMatchingDayFilter() {
        assertThatThrownBy(() -> parse("""
                {
                  "kind": "MONTH_NTH_WORKDAY",
                  "calendarCode": "cn-official",
                  "dayFilter": { "type": "WORKDAY" },
                  "times": ["09:00"]
                }
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("MONTH_NTH_WORKDAY");
    }

    @Test
    void acceptsMonthNthWorkday() {
        ScheduleSpec spec = parse("""
                {
                  "kind": "MONTH_NTH_WORKDAY",
                  "calendarCode": "cn-official",
                  "dayFilter": { "type": "MONTH_NTH_WORKDAY", "nth": 1 },
                  "times": ["09:00"]
                }
                """);

        assertThat(spec.dayFilter().type()).isEqualTo(DayFilterType.MONTH_NTH_WORKDAY);
        assertThat(spec.dayFilter().nth()).isEqualTo(1);
    }

    @Test
    void roundTripsThroughCanonicalJson() {
        ScheduleSpec spec = parse("""
                {
                  "kind": "CALENDAR_TIMES",
                  "calendarCode": "cn-official",
                  "dayFilter": { "type": "MONTH_NTH_WORKDAY", "nth": 2 },
                  "times": ["09:00", "18:00"],
                  "holidayPolicy": "NEXT_WORKDAY"
                }
                """);

        ScheduleSpec reparsed = codec.parse(codec.write(spec));

        assertThat(reparsed).isEqualTo(spec);
        assertThat(reparsed.interval()).isNull();
        assertThat(reparsed.holidayPolicy()).isEqualTo(HolidayPolicy.NEXT_WORKDAY);
        assertThat(Duration.ofMinutes(10)).isEqualTo(Duration.parse("PT10M"));
    }
}
