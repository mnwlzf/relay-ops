package com.relayops.scheduling.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;

@TableName("scheduling_calendar_day")
public class SchedulingCalendarDayEntity {

    private Long calendarId;
    private LocalDate day;
    private String dayType;
    private String name;
    private String origin;

    public Long getCalendarId() { return calendarId; }
    public void setCalendarId(Long calendarId) { this.calendarId = calendarId; }
    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }
    public String getDayType() { return dayType; }
    public void setDayType(String dayType) { this.dayType = dayType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
}
