package com.relayops.assistant.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.relayops.infrastructure.persistence.mybatis.handler.JsonbStringTypeHandler;
import java.time.OffsetDateTime;

@TableName(value = "assistant_profile", autoResultMap = true)
public class AssistantProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String scopeType;
    private String scopeKey;
    private String packId;
    private Long personaId;
    private String modelProfile;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String memoryPolicy;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String guardPolicy;
    private Boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getPackId() { return packId; }
    public void setPackId(String packId) { this.packId = packId; }
    public Long getPersonaId() { return personaId; }
    public void setPersonaId(Long personaId) { this.personaId = personaId; }
    public String getModelProfile() { return modelProfile; }
    public void setModelProfile(String modelProfile) { this.modelProfile = modelProfile; }
    public String getMemoryPolicy() { return memoryPolicy; }
    public void setMemoryPolicy(String memoryPolicy) { this.memoryPolicy = memoryPolicy; }
    public String getGuardPolicy() { return guardPolicy; }
    public void setGuardPolicy(String guardPolicy) { this.guardPolicy = guardPolicy; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
