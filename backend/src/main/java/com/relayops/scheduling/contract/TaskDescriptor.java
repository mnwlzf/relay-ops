package com.relayops.scheduling.contract;

import com.relayops.scheduling.domain.ScheduleKind;
import java.util.Set;

/**
 * Self-description of a task type, used by the admin page to render forms and by validation
 * to reject incompatible schedules.
 *
 * @param title        human readable name
 * @param description  what the task does and what its params mean
 * @param allowedKinds schedule kinds this handler supports
 * @param paramsSchema optional JSON Schema of {@code params} (may be {@code null})
 */
public record TaskDescriptor(
        String title,
        String description,
        Set<ScheduleKind> allowedKinds,
        String paramsSchema
) {

    public TaskDescriptor {
        allowedKinds = allowedKinds == null ? Set.of() : Set.copyOf(allowedKinds);
    }

    public boolean supports(ScheduleKind kind) {
        return allowedKinds.isEmpty() || allowedKinds.contains(kind);
    }
}
