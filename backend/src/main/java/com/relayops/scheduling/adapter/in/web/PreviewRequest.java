package com.relayops.scheduling.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/v1/scheduling/spec/preview}. */
public record PreviewRequest(
        @NotNull(message = "必须提供 spec") JsonNode spec,
        Integer count
) {
}
