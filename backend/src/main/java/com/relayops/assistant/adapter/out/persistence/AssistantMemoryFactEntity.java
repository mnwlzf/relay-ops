package com.relayops.assistant.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.relayops.infrastructure.persistence.mybatis.handler.JsonbStringTypeHandler;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@TableName(value = "assistant_memory_fact", autoResultMap = true)
public class AssistantMemoryFactEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String namespace;
    private String scopeType;
    private String scopeKey;
    private String category;
    private String factKey;
    private String factValue;
    private BigDecimal confidence;
    private String source;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String provenance;
    private OffsetDateTime expiresAt;
    private OffsetDateTime lastUsedAt;
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getScopeKey() { return scopeKey; }
    public void setScopeKey(String scopeKey) { this.scopeKey = scopeKey; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getFactKey() { return factKey; }
    public void setFactKey(String factKey) { this.factKey = factKey; }
    public String getFactValue() { return factValue; }
    public void setFactValue(String factValue) { this.factValue = factValue; }
    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(OffsetDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
