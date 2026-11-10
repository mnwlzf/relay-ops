package com.relayops.agent.contract;

import java.time.Duration;
import java.util.Objects;

/**
 * Metadata plus handler of one tool.
 *
 * <p>{@code inputSchema} is a JSON Schema fragment used by the admin page to render a parameter form;
 * it lets new tools appear in the UI without frontend changes.</p>
 */
public record ToolSpec(
        String id,
        String title,
        String description,
        SideEffect sideEffect,
        boolean schedulable,
        ToolAccess access,
        RedactionPolicy redaction,
        Duration timeout,
        String inputSchema,
        ToolHandler handler
) {

    public ToolSpec {
        id = id == null ? "" : id.strip();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("tool id 不能为空");
        }
        title = title == null || title.isBlank() ? id : title.strip();
        description = description == null ? "" : description.strip();
        sideEffect = sideEffect == null ? SideEffect.READ_ONLY : sideEffect;
        access = access == null ? ToolAccess.everyone() : access;
        redaction = redaction == null ? RedactionPolicy.NONE : redaction;
        timeout = timeout == null || timeout.isNegative() || timeout.isZero() ? Duration.ofSeconds(10) : timeout;
        Objects.requireNonNull(handler, "handler");
    }

    /** Whether a scheduled message may reference this tool. */
    public boolean allowsScheduling() {
        return schedulable && sideEffect.schedulable();
    }

    public ToolSpec withSchedulable(boolean value) {
        return new ToolSpec(id, title, description, sideEffect, value, access, redaction, timeout, inputSchema, handler);
    }
}
