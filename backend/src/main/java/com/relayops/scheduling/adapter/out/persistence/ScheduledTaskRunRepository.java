package com.relayops.scheduling.adapter.out.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.scheduling.domain.RunStatus;
import com.relayops.scheduling.domain.TaskRunRecord;
import com.relayops.scheduling.domain.TriggerSource;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** Persistence for {@code scheduling_task_run}, including the idempotent start. */
@Repository
public class ScheduledTaskRunRepository {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskRunRepository.class);

    private final ScheduledTaskRunMapper mapper;
    private final ObjectMapper objectMapper;

    public ScheduledTaskRunRepository(ScheduledTaskRunMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Opens a run row.
     *
     * @return the run id, or {@link Optional#empty()} when this occurrence already ran
     */
    public Optional<Long> start(long taskId, Instant plannedAt, TriggerSource source, Instant now) {
        ScheduledTaskRunEntity entity = new ScheduledTaskRunEntity();
        entity.setTaskId(taskId);
        entity.setPlannedAt(OffsetDateTime.ofInstant(plannedAt, ZoneId.systemDefault()));
        entity.setTriggerSource(source.name());
        entity.setStartedAt(OffsetDateTime.ofInstant(now, ZoneId.systemDefault()));
        entity.setStatus(RunStatus.RUNNING.name());
        try {
            mapper.insert(entity);
            return Optional.of(entity.getId());
        } catch (DuplicateKeyException ex) {
            log.debug("跳过重复触发: taskId={}, plannedAt={}", taskId, plannedAt);
            return Optional.empty();
        }
    }

    public void finish(long runId, RunStatus status, Instant finishedAt, long durationMs,
                       String message, JsonNode detail) {
        ScheduledTaskRunEntity entity = new ScheduledTaskRunEntity();
        entity.setId(runId);
        entity.setStatus(status.name());
        entity.setFinishedAt(OffsetDateTime.ofInstant(finishedAt, ZoneId.systemDefault()));
        entity.setDurationMs((int) Math.min(Integer.MAX_VALUE, durationMs));
        entity.setMessage(truncate(message, 1000));
        entity.setDetail(detail == null ? null : write(detail));
        mapper.updateById(entity);
    }

    /** Marks every dangling RUNNING row as FAILED; used at startup after a crash. */
    public void failStaleRuns(Instant now, String message) {
        mapper.failRunningRows(OffsetDateTime.ofInstant(now, ZoneId.systemDefault()), truncate(message, 1000));
    }

    public boolean existsRunning(long taskId) {
        return mapper.countRunning(taskId) > 0;
    }

    public List<TaskRunRecord> recent(long taskId, int limit) {
        return mapper.selectRecent(taskId, limit).stream().map(this::toRecord).toList();
    }

    private TaskRunRecord toRecord(ScheduledTaskRunEntity entity) {
        return new TaskRunRecord(
                entity.getId(),
                entity.getTaskId(),
                toInstant(entity.getPlannedAt()),
                TriggerSource.valueOf(entity.getTriggerSource()),
                toInstant(entity.getStartedAt()),
                toInstant(entity.getFinishedAt()),
                RunStatus.valueOf(entity.getStatus()),
                entity.getDurationMs(),
                entity.getMessage(),
                read(entity.getDetail()));
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private String write(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            return null;
        }
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
