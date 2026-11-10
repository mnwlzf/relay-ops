package com.relayops.scheduling.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ScheduledTaskRunMapper extends BaseMapper<ScheduledTaskRunEntity> {

    @Select("select count(*) from scheduling_task_run where task_id = #{taskId} and status = 'RUNNING'")
    int countRunning(@Param("taskId") long taskId);

    @Select("""
            select * from scheduling_task_run
             where task_id = #{taskId}
             order by started_at desc
             limit #{limit}
            """)
    List<ScheduledTaskRunEntity> selectRecent(@Param("taskId") long taskId, @Param("limit") int limit);

    /** After a crash, executions left RUNNING are closed as FAILED so concurrency checks can recover. */
    @Update("""
            update scheduling_task_run
               set status = 'FAILED',
                   finished_at = #{now},
                   message = #{message}
             where status = 'RUNNING'
            """)
    int failRunningRows(@Param("now") OffsetDateTime now, @Param("message") String message);
}
