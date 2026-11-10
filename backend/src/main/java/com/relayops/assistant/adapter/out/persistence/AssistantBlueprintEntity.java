package com.relayops.assistant.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.relayops.infrastructure.persistence.mybatis.handler.JsonbStringTypeHandler;
import java.time.OffsetDateTime;

@TableName(value = "assistant_blueprint", autoResultMap = true)
public class AssistantBlueprintEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String packId;
    private Long personaId;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String segments;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String options;
    private Boolean enabled;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPackId() { return packId; }
    public void setPackId(String packId) { this.packId = packId; }
    public Long getPersonaId() { return personaId; }
    public void setPersonaId(Long personaId) { this.personaId = personaId; }
    public String getSegments() { return segments; }
    public void setSegments(String segments) { this.segments = segments; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
