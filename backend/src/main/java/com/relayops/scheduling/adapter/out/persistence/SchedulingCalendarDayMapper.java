package com.relayops.scheduling.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Calendar days use a composite key, so this mapper stays on explicit SQL instead of BaseMapper. */
@Mapper
public interface SchedulingCalendarDayMapper {

    @Select("""
            select calendar_id, day, day_type, name, origin
              from scheduling_calendar_day
             where calendar_id = #{calendarId}
             order by day
            """)
    List<SchedulingCalendarDayEntity> selectByCalendar(@Param("calendarId") long calendarId);

    @Select("""
            select calendar_id, day, day_type, name, origin
              from scheduling_calendar_day
             where calendar_id = #{calendarId} and day between #{from} and #{to}
             order by day
            """)
    List<SchedulingCalendarDayEntity> selectRange(
            @Param("calendarId") long calendarId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Insert("""
            insert into scheduling_calendar_day (calendar_id, day, day_type, name, origin)
            values (#{calendarId}, #{day}, #{dayType}, #{name}, #{origin})
            on conflict (calendar_id, day) do update
               set day_type = excluded.day_type,
                   name = excluded.name,
                   origin = excluded.origin
            """)
    int upsert(SchedulingCalendarDayEntity entity);

    @Delete("delete from scheduling_calendar_day where calendar_id = #{calendarId} and day = #{day}")
    int deleteDay(@Param("calendarId") long calendarId, @Param("day") LocalDate day);

    @Select("select count(*) from scheduling_calendar_day where calendar_id = #{calendarId}")
    int countByCalendar(@Param("calendarId") long calendarId);
}
