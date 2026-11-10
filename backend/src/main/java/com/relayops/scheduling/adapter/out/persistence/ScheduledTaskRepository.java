package com.relayops.scheduling.adapter.out.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.relayops.scheduling.application.plan.ScheduleSpecCodec;
import com.relayops.scheduling.domain.ConcurrencyPolicy;
import com.relayops.scheduling.domain.MisfirePolicy;
import com.relayops.scheduling.domain.QuietHours;
import com.relayops.scheduling.domain.RunStatus;
import com.relayops.scheduling.domain.ScheduleSpec;
import com.relayops.scheduling.domain.TaskDefinition;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.error.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Persistence for {@code scheduling_task}; converts between the entity and the domain record. */
@Repository
public class ScheduledTaskRepository {

    private final ScheduledTaskMapper mapper;
    private final ScheduleSpecCodec codec;
    private final ObjectMapper objectMapper;

    public ScheduledTaskRepository(ScheduledTaskMapper mapper, ScheduleSpecCodec codec, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.codec = codec;
        this.objectMapper = objectMapper;
    }

    public Optional<TaskDefinition> find(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDefinition);
    }

    public TaskDefinition get(long id) {
        return find(id).orElseThrow(() -> new ResourceNotFoundException(
                "SCHEDULING_TASK_NOT_FOUND", "定时任务不存在: " + id));
    }

    public List<TaskDefinition> findAll() {
        return mapper.selectAllTasks().stream().map(this::toDefinition).toList();
    }

    public List<TaskDefinition> findDue(Instant now, int limit) {
        return mapper.selectDue(OffsetDateTime.ofInstant(now, ZoneId.systemDefault()), limit).stream()
                .map(this::toDefinition)
                .toList();
    }

    public TaskDefinition insert(TaskDefinition task) {
        OffsetDateTime now = OffsetDateTime.now();
        ScheduledTaskEntity entity = new ScheduledTaskEntity();
        apply(entity, task);
        entity.setVersion(0);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        mapper.insert(entity);
        return toDefinition(mapper.selectById(entity.getId()));
    }

    public TaskDefinition update(TaskDefinition task) {
        if (task.id() == null) {
            throw new BusinessException("SCHEDULING_TASK_INVALID", "更新任务时缺少 id");
        }
        ScheduledTaskEntity entity = new ScheduledTaskEntity();
        apply(entity, task);
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setVersion(task.version() + 1);
        int updated = mapper.update(entity, Wrappers.<ScheduledTaskEntity>lambdaUpdate()
                .eq(ScheduledTaskEntity::getId, task.id())
                .eq(ScheduledTaskEntity::getVersion, task.version()));
        if (updated == 0) {
            throw new BusinessException("SCHEDULING_CONFLICT", "任务已被修改，请刷新后重试");
        }
        return toDefinition(mapper.selectById(task.id()));
    }

    public void delete(long id) {
        mapper.deleteById(id);
    }

    public void updateEnabled(long id, boolean enabled) {
        ScheduledTaskEntity entity = new ScheduledTaskEntity();
        entity.setId(id);
        entity.setEnabled(enabled);
        entity.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(entity);
    }

    public void updateNextFireAt(long id, Instant nextFireAt) {
        // Explicit set(...) so null is written instead of skipped by the NOT_NULL update strategy.
        mapper.update(null, Wrappers.<ScheduledTaskEntity>lambdaUpdate()
                .eq(ScheduledTaskEntity::getId, id)
                .set(ScheduledTaskEntity::getNextFireAt,
                        nextFireAt == null ? null : OffsetDateTime.ofInstant(nextFireAt, ZoneId.systemDefault())));
    }

    public void updateLastRun(long id, Instant at, RunStatus status, String message) {
        mapper.update(null, Wrappers.<ScheduledTaskEntity>lambdaUpdate()
                .eq(ScheduledTaskEntity::getId, id)
                .set(ScheduledTaskEntity::getLastRunAt,
                        at == null ? null : OffsetDateTime.ofInstant(at, ZoneId.systemDefault()))
                .set(ScheduledTaskEntity::getLastRunStatus, status == null ? null : status.name())
                .set(ScheduledTaskEntity::getLastRunMessage, truncate(message, 500))
                .set(ScheduledTaskEntity::getUpdatedAt, OffsetDateTime.now()));
    }

    private void apply(ScheduledTaskEntity entity, TaskDefinition task) {
        entity.setTaskType(task.taskType());
        entity.setName(task.name());
        entity.setKind(task.spec().kind().name());
        entity.setSpec(codec.writeString(task.spec()));
        entity.setTimezone(task.spec().zone().getId());
        entity.setCalendarCode(task.spec().calendarCode());
        entity.setParams(writeJson(task.params()));
        entity.setMisfirePolicy(task.misfirePolicy().name());
        entity.setMaxCatchUp(task.maxCatchUp());
        entity.setConcurrencyPolicy(task.concurrencyPolicy().name());
        entity.setTimeoutSeconds(task.timeoutSeconds());
        entity.setRetryMax(task.retryMax());
        entity.setRetryBackoffSeconds(task.retryBackoffSeconds());
        entity.setQuietHours(writeQuietHours(task.quietHours()));
        entity.setEnabled(task.enabled());
        entity.setNextFireAt(task.nextFireAt() == null
                ? null
                : OffsetDateTime.ofInstant(task.nextFireAt(), ZoneId.systemDefault()));
    }

    private TaskDefinition toDefinition(ScheduledTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        ScheduleSpec spec = codec.parse(readTree(entity.getSpec()));
        ZoneId zone = ZoneId.of(entity.getTimezone() == null ? spec.zone().getId() : entity.getTimezone());
        return new TaskDefinition(
                entity.getId(),
                entity.getTaskType(),
                entity.getName(),
                spec,
                readTree(entity.getParams()),
                MisfirePolicy.valueOf(entity.getMisfirePolicy()),
                entity.getMaxCatchUp() == null ? 1 : entity.getMaxCatchUp(),
                ConcurrencyPolicy.valueOf(entity.getConcurrencyPolicy()),
                entity.getTimeoutSeconds() == null ? 300 : entity.getTimeoutSeconds(),
                entity.getRetryMax() == null ? 0 : entity.getRetryMax(),
                entity.getRetryBackoffSeconds() == null ? 60 : entity.getRetryBackoffSeconds(),
                readQuietHours(entity.getQuietHours()),
                Boolean.TRUE.equals(entity.getEnabled()),
                toInstant(entity.getNextFireAt()),
                toInstant(entity.getLastRunAt()),
                entity.getLastRunStatus(),
                entity.getLastRunMessage(),
                entity.getVersion() == null ? 0 : entity.getVersion(),
                toInstant(entity.getCreatedAt()),
                toInstant(entity.getUpdatedAt()));
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("数据库中存的 JSON 无法解析", ex);
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null ? JsonNodeFactory.instance.objectNode() : node);
        } catch (Exception ex) {
            throw new IllegalStateException("无法序列化 JSON", ex);
        }
    }

    private String writeQuietHours(QuietHours quietHours) {
        if (quietHours == null) {
            return null;
        }
        List<String> values = new ArrayList<>(2);
        values.add(quietHours.from().toString());
        values.add(quietHours.to().toString());
        return writeJson(objectMapper.valueToTree(values));
    }

    private QuietHours readQuietHours(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        JsonNode node = readTree(json);
        if (!node.isArray() || node.size() != 2) {
            return null;
        }
        return QuietHours.of(LocalTime.parse(node.get(0).asText()), LocalTime.parse(node.get(1).asText()));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
