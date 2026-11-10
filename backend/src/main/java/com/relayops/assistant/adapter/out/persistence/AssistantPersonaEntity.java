package com.relayops.assistant.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.relayops.infrastructure.persistence.mybatis.handler.JsonbStringTypeHandler;
import java.time.OffsetDateTime;

@TableName(value = "assistant_persona", autoResultMap = true)
public class AssistantPersonaEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private String appearancePrompt;
    private String personalityPrompt;
    private String rhythmPrompt;
    private String relationPrompt;
    private String emotionPrompt;
    private String boundaryPrompt;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String style;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String examples;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String addressRules;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String packs;
    private Boolean enabled;
    private Boolean isDefault;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getAppearancePrompt() { return appearancePrompt; }
    public void setAppearancePrompt(String appearancePrompt) { this.appearancePrompt = appearancePrompt; }
    public String getPersonalityPrompt() { return personalityPrompt; }
    public void setPersonalityPrompt(String personalityPrompt) { this.personalityPrompt = personalityPrompt; }
    public String getRhythmPrompt() { return rhythmPrompt; }
    public void setRhythmPrompt(String rhythmPrompt) { this.rhythmPrompt = rhythmPrompt; }
    public String getRelationPrompt() { return relationPrompt; }
    public void setRelationPrompt(String relationPrompt) { this.relationPrompt = relationPrompt; }
    public String getEmotionPrompt() { return emotionPrompt; }
    public void setEmotionPrompt(String emotionPrompt) { this.emotionPrompt = emotionPrompt; }
    public String getBoundaryPrompt() { return boundaryPrompt; }
    public void setBoundaryPrompt(String boundaryPrompt) { this.boundaryPrompt = boundaryPrompt; }
    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
    public String getExamples() { return examples; }
    public void setExamples(String examples) { this.examples = examples; }
    public String getAddressRules() { return addressRules; }
    public void setAddressRules(String addressRules) { this.addressRules = addressRules; }
    public String getPacks() { return packs; }
    public void setPacks(String packs) { this.packs = packs; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
