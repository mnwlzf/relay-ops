package com.relayops.scheduling.adapter.out.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.relayops.infrastructure.persistence.mybatis.handler.JsonbStringTypeHandler;
import java.time.OffsetDateTime;

@TableName(value = "scheduling_task_run", autoResultMap = true)
public class ScheduledTaskRunEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private OffsetDateTime plannedAt;
    private String triggerSource;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private String status;
    private Integer durationMs;
    private String message;
    @TableField(typeHandler = JsonbStringTypeHandler.class)
    private String detail;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public OffsetDateTime getPlannedAt() { return plannedAt; }
    public void setPlannedAt(OffsetDateTime plannedAt) { this.plannedAt = plannedAt; }
    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
