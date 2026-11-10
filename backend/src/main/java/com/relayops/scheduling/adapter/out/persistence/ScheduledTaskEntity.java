package com.relayops.scheduling.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.relayops.infrastructure.persistence.mybatis.handler.JsonbStringTypeHandler;
import java.time.OffsetDateTime;

@TableName(value = "scheduling_task", autoResultMap = true)
public class ScheduledTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskType;
    private String name;
    private String kind;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String spec;
    private String timezone;
    private String calendarCode;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String params;
    private String misfirePolicy;
    private Integer maxCatchUp;
    private String concurrencyPolicy;
    private Integer timeoutSeconds;
    private Integer retryMax;
    private Integer retryBackoffSeconds;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String quietHours;
    private Boolean enabled;
    private OffsetDateTime nextFireAt;
    private OffsetDateTime lastRunAt;
    private String lastRunStatus;
    private String lastRunMessage;
    private Integer version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getSpec() { return spec; }
    public void setSpec(String spec) { this.spec = spec; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public String getCalendarCode() { return calendarCode; }
    public void setCalendarCode(String calendarCode) { this.calendarCode = calendarCode; }
    public String getParams() { return params; }
    public void setParams(String params) { this.params = params; }
    public String getMisfirePolicy() { return misfirePolicy; }
    public void setMisfirePolicy(String misfirePolicy) { this.misfirePolicy = misfirePolicy; }
    public Integer getMaxCatchUp() { return maxCatchUp; }
    public void setMaxCatchUp(Integer maxCatchUp) { this.maxCatchUp = maxCatchUp; }
    public String getConcurrencyPolicy() { return concurrencyPolicy; }
    public void setConcurrencyPolicy(String concurrencyPolicy) { this.concurrencyPolicy = concurrencyPolicy; }
    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Integer getRetryMax() { return retryMax; }
    public void setRetryMax(Integer retryMax) { this.retryMax = retryMax; }
    public Integer getRetryBackoffSeconds() { return retryBackoffSeconds; }
    public void setRetryBackoffSeconds(Integer retryBackoffSeconds) { this.retryBackoffSeconds = retryBackoffSeconds; }
    public String getQuietHours() { return quietHours; }
    public void setQuietHours(String quietHours) { this.quietHours = quietHours; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public OffsetDateTime getNextFireAt() { return nextFireAt; }
    public void setNextFireAt(OffsetDateTime nextFireAt) { this.nextFireAt = nextFireAt; }
    public OffsetDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(OffsetDateTime lastRunAt) { this.lastRunAt = lastRunAt; }
    public String getLastRunStatus() { return lastRunStatus; }
    public void setLastRunStatus(String lastRunStatus) { this.lastRunStatus = lastRunStatus; }
    public String getLastRunMessage() { return lastRunMessage; }
    public void setLastRunMessage(String lastRunMessage) { this.lastRunMessage = lastRunMessage; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
