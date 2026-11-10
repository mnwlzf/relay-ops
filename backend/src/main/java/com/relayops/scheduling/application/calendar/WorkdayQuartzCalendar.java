package com.relayops.scheduling.application.calendar;

import com.relayops.scheduling.domain.day.CalendarData;
import com.relayops.scheduling.domain.day.DayMatcher;
import java.time.Instant;
import java.time.ZoneId;
import org.quartz.Calendar;

/**
 * Bridges the work calendar into Quartz.
 *
 * <p>Quartz calendars can only exclude dates, but Chinese schedules also need to include
 * make-up workdays that fall on weekends. Implementing {@link Calendar#isTimeIncluded(long)}
 * directly turns the table around: the trigger says "every day at 09:00" and this calendar answers
 * "is this date a workday?".</p>
 */
public final class WorkdayQuartzCalendar implements Calendar {

    private static final int MAX_LOOKAHEAD_DAYS = 400;

    private final DayMatcher matcher;
    private final CalendarData data;
    private final ZoneId zone;
    private String description;

    public WorkdayQuartzCalendar(DayMatcher matcher, CalendarData data, ZoneId zone, String description) {
        this.matcher = matcher;
        this.data = data;
        this.zone = zone;
        this.description = description;
    }

    @Override
    public boolean isTimeIncluded(long timeStamp) {
        return matcher.matches(toLocalDate(timeStamp), data);
    }

    @Override
    public long getNextIncludedTime(long timeStamp) {
        long cursor = timeStamp;
        for (int day = 0; day < MAX_LOOKAHEAD_DAYS; day++) {
            if (isTimeIncluded(cursor)) {
                return cursor;
            }
            cursor = nextDayStart(cursor);
        }
        return timeStamp;
    }

    @Override
    public Calendar getBaseCalendar() {
        return null;
    }

    @Override
    public void setBaseCalendar(Calendar baseCalendar) {
        // not used: this calendar is self-contained
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    /** Quartz clones calendars when they are registered; the state here is immutable except the label. */
    @Override
    public Object clone() {
        return new WorkdayQuartzCalendar(matcher, data, zone, description);
    }

    private java.time.LocalDate toLocalDate(long timeStamp) {
        return Instant.ofEpochMilli(timeStamp).atZone(zone).toLocalDate();
    }

    private long nextDayStart(long timeStamp) {
        return Instant.ofEpochMilli(timeStamp)
                .atZone(zone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli();
    }
}
