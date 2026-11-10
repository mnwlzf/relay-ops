package com.relayops.scheduling.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ScheduledTaskMapper extends BaseMapper<ScheduledTaskEntity> {

    /** Tasks whose persisted next fire time has passed; used for startup catch-up. */
    @Select("""
            select * from scheduling_task
             where enabled = true and next_fire_at is not null and next_fire_at <= #{now}
             order by next_fire_at
             limit #{limit}
            """)
    List<ScheduledTaskEntity> selectDue(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    @Select("select * from scheduling_task order by id")
    List<ScheduledTaskEntity> selectAllTasks();
}
