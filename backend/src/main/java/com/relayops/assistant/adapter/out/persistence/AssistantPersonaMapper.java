package com.relayops.assistant.adapter.out.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssistantPersonaMapper extends BaseMapper<AssistantPersonaEntity> {

    @Select("select * from assistant_persona where is_default = true limit 1")
    AssistantPersonaEntity selectDefault();

    @Select("select * from assistant_persona where enabled = true order by name")
    List<AssistantPersonaEntity> selectEnabled();
}
