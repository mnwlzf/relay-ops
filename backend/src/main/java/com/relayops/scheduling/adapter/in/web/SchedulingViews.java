package com.relayops.scheduling.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.relayops.scheduling.contract.ScheduledTaskHandler;
import com.relayops.scheduling.domain.TaskRunRecord;
import java.time.Instant;
import java.util.List;

/** Read models shared by the scheduling API. */
public final class SchedulingViews {

    private SchedulingViews() {
    }

    /** One execution of a task. */
    public record TaskRunResponse(
            Long id,
            Instant plannedAt,
            String source,
            Instant startedAt,
            Instant finishedAt,
            String status,
            Integer durationMs,
            String message,
            JsonNode detail
    ) {

        public static TaskRunResponse of(TaskRunRecord run) {
            return new TaskRunResponse(run.id(), run.plannedAt(), run.source().name(), run.startedAt(),
                    run.finishedAt(), run.status().name(), run.durationMs(), run.message(), run.detail());
        }
    }

    /** A task type that handlers can offer to the admin page. */
    public record TaskTypeResponse(
            String taskType,
            String title,
            String description,
            List<String> allowedKinds,
            String paramsSchema
    ) {

        public static TaskTypeResponse of(ScheduledTaskHandler handler) {
            return new TaskTypeResponse(
                    handler.taskType(),
                    handler.describe().title(),
                    handler.describe().description(),
                    handler.describe().allowedKinds().stream().map(Enum::name).sorted().toList(),
                    handler.describe().paramsSchema());
        }
    }
}
