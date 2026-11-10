package com.relayops.scheduling.adapter.out.persistence;

import com.relayops.scheduling.domain.day.CalendarData;
import com.relayops.scheduling.domain.day.DayType;
import com.relayops.shared.error.BusinessException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Persistence for work calendars; only exceptions are stored. */
@Repository
public class SchedulingCalendarRepository {

    private final SchedulingCalendarMapper calendarMapper;
    private final SchedulingCalendarDayMapper dayMapper;

    public SchedulingCalendarRepository(SchedulingCalendarMapper calendarMapper,
                                        SchedulingCalendarDayMapper dayMapper) {
        this.calendarMapper = calendarMapper;
        this.dayMapper = dayMapper;
    }

    public Optional<CalendarInfo> find(String code) {
        SchedulingCalendarEntity entity = calendarMapper.selectByCode(code);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(new CalendarInfo(entity.getId(), entity.getCode(), entity.getName(),
                entity.getTimezone(), entity.getSource(), entity.getCoveredTo(), entity.getDescription()));
    }

    public CalendarInfo get(String code) {
        return find(code).orElseThrow(() -> new BusinessException(
                "SCHEDULING_CALENDAR_NOT_FOUND", "工作日历不存在: " + code));
    }

    public List<CalendarInfo> findAll() {
        return calendarMapper.selectList(null).stream()
                .map(entity -> new CalendarInfo(entity.getId(), entity.getCode(), entity.getName(),
                        entity.getTimezone(), entity.getSource(), entity.getCoveredTo(),
                        entity.getDescription()))
                .toList();
    }

    /** Loads overrides for the given calendar code; a missing code yields an empty calendar. */
    public CalendarData load(String code) {
        if (code == null || code.isBlank()) {
            return CalendarData.empty(null);
        }
        CalendarInfo info = find(code).orElseThrow(() -> new BusinessException(
                "SCHEDULING_CALENDAR_NOT_FOUND", "工作日历不存在: " + code));
        Map<LocalDate, DayType> overrides = new LinkedHashMap<>();
        for (SchedulingCalendarDayEntity day : dayMapper.selectByCalendar(info.id())) {
            overrides.put(day.getDay(), DayType.valueOf(day.getDayType()));
        }
        return new CalendarData(code, overrides);
    }

    public List<CalendarDay> days(String code, LocalDate from, LocalDate to) {
        CalendarInfo info = get(code);
        return dayMapper.selectRange(info.id(), from, to).stream()
                .map(entity -> new CalendarDay(entity.getDay(), DayType.valueOf(entity.getDayType()),
                        entity.getName(), entity.getOrigin()))
                .toList();
    }

    public void upsertDays(String code, List<CalendarDay> days) {
        CalendarInfo info = get(code);
        for (CalendarDay day : days) {
            SchedulingCalendarDayEntity entity = new SchedulingCalendarDayEntity();
            entity.setCalendarId(info.id());
            entity.setDay(day.day());
            entity.setDayType(day.dayType().name());
            entity.setName(day.name());
            entity.setOrigin(day.origin() == null ? "MANUAL" : day.origin());
            dayMapper.upsert(entity);
        }
        LocalDate latest = days.stream().map(CalendarDay::day).max(LocalDate::compareTo).orElse(null);
        if (latest != null && (info.coveredTo() == null || latest.isAfter(info.coveredTo()))) {
            updateCoveredTo(code, latest);
        }
    }

    public void deleteDay(String code, LocalDate day) {
        CalendarInfo info = get(code);
        dayMapper.deleteDay(info.id(), day);
    }

    public void updateCoveredTo(String code, LocalDate coveredTo) {
        CalendarInfo info = get(code);
        SchedulingCalendarEntity entity = new SchedulingCalendarEntity();
        entity.setId(info.id());
        entity.setCoveredTo(coveredTo);
        entity.setUpdatedAt(java.time.OffsetDateTime.now());
        calendarMapper.updateById(entity);
    }

    public int countDays(String code) {
        return dayMapper.countByCalendar(get(code).id());
    }

    /** Calendar header plus how many exception days it currently holds. */
    public record CalendarInfo(Long id, String code, String name, String timezone, String source,
                               LocalDate coveredTo, String description) {
    }

    public record CalendarDay(LocalDate day, DayType dayType, String name, String origin) {
    }
}
