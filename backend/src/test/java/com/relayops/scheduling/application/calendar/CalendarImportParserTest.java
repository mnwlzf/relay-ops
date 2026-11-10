package com.relayops.scheduling.application.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relayops.scheduling.application.calendar.CalendarImportParser.Format;
import com.relayops.scheduling.application.calendar.CalendarImportParser.ImportedDay;
import com.relayops.scheduling.domain.day.DayType;
import com.relayops.shared.error.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CalendarImportParserTest {

    @Test
    void parsesJsonArray() {
        String content = """
                [
                  { "date": "2026-10-01", "type": "HOLIDAY", "name": "国庆" },
                  { "day": "2026-10-10", "dayType": "MAKEUP_WORKDAY", "name": "补班" }
                ]
                """;

        List<ImportedDay> days = CalendarImportParser.parse(content, Format.AUTO);

        assertThat(days).hasSize(2);
        assertThat(days.get(0).day()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(days.get(0).dayType()).isEqualTo(DayType.HOLIDAY);
        assertThat(days.get(1).dayType()).isEqualTo(DayType.MAKEUP_WORKDAY);
    }

    @Test
    void parsesHolidayCnShapeWithOffDayFlag() {
        String content = """
                {
                  "year": 2026,
                  "days": [
                    { "name": "国庆节", "date": "2026-10-01", "isOffDay": true },
                    { "name": "国庆节后上班", "date": "2026-10-10", "isOffDay": false }
                  ]
                }
                """;

        List<ImportedDay> days = CalendarImportParser.parse(content, Format.JSON);

        assertThat(days).hasSize(2);
        assertThat(days.get(0).dayType()).isEqualTo(DayType.HOLIDAY);
        assertThat(days.get(1).dayType()).isEqualTo(DayType.MAKEUP_WORKDAY);
        assertThat(days.get(1).name()).isEqualTo("国庆节后上班");
    }

    @Test
    void parsesPlainTextLines() {
        String content = """
                # 2026 年国庆
                2026-10-01 HOLIDAY 国庆
                2026-10-10 MAKEUP_WORKDAY 补班
                2026-10-11 补班
                """;

        List<ImportedDay> days = CalendarImportParser.parse(content, Format.TEXT);

        assertThat(days).hasSize(3);
        assertThat(days.get(0).name()).isEqualTo("国庆");
        assertThat(days.get(2).dayType()).isEqualTo(DayType.MAKEUP_WORKDAY);
    }

    @Test
    void parsesIcsEvents() {
        String content = """
                BEGIN:VCALENDAR
                VERSION:2.0
                BEGIN:VEVENT
                DTSTART;VALUE=DATE:20261001
                SUMMARY:国庆节
                END:VEVENT
                BEGIN:VEVENT
                DTSTART;VALUE=DATE:20261010
                SUMMARY:国庆节后补班
                END:VEVENT
                END:VCALENDAR
                """;

        List<ImportedDay> days = CalendarImportParser.parse(content, Format.AUTO);

        assertThat(days).hasSize(2);
        assertThat(days.get(0).day()).isEqualTo(LocalDate.parse("2026-10-01"));
        assertThat(days.get(0).dayType()).isEqualTo(DayType.HOLIDAY);
        assertThat(days.get(1).dayType()).isEqualTo(DayType.MAKEUP_WORKDAY);
    }

    @Test
    void laterDuplicateWins() {
        String content = """
                [
                  { "date": "2026-10-10", "type": "HOLIDAY" },
                  { "date": "2026-10-10", "type": "MAKEUP_WORKDAY" }
                ]
                """;

        List<ImportedDay> days = CalendarImportParser.parse(content, Format.JSON);

        assertThat(days).hasSize(1);
        assertThat(days.get(0).dayType()).isEqualTo(DayType.MAKEUP_WORKDAY);
    }

    @Test
    void rejectsEmptyAndUnparseableContent() {
        assertThatThrownBy(() -> CalendarImportParser.parse("   ", Format.AUTO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入内容为空");

        assertThatThrownBy(() -> CalendarImportParser.parse("hello world", Format.AUTO))
                .isInstanceOf(BusinessException.class);
    }
}
