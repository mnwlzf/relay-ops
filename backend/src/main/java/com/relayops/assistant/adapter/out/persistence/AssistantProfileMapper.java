package com.relayops.assistant.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssistantProfileMapper extends BaseMapper<AssistantProfileEntity> {

    @Select("select * from assistant_profile where pack_id = #{packId} and enabled = true")
    List<AssistantProfileEntity> selectByPack(@Param("packId") String packId);

    @Select("""
            select * from assistant_profile
             where scope_type = #{scopeType} and scope_key = #{scopeKey} and pack_id = #{packId}
             limit 1
            """)
    AssistantProfileEntity selectOne(@Param("scopeType") String scopeType,
                                     @Param("scopeKey") String scopeKey,
                                     @Param("packId") String packId);
}
