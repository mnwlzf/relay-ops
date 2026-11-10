package com.relayops.scheduling.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SchedulingCalendarMapper extends BaseMapper<SchedulingCalendarEntity> {

    @Select("select * from scheduling_calendar where code = #{code}")
    SchedulingCalendarEntity selectByCode(@Param("code") String code);
}
