package com.relayops.assistant.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssistantBlueprintMapper extends BaseMapper<AssistantBlueprintEntity> {

    @Select("select * from assistant_blueprint where code = #{code}")
    AssistantBlueprintEntity selectByCode(@Param("code") String code);
}
