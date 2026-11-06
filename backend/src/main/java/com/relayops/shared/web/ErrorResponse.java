package com.relayops.shared.web;

import java.time.Instant;
import java.util.Map;

/**
 * Stable error envelope. HTTP status is kept separate from the application error code.
 */
public record ErrorResponse(
        String code,
        String message,
        String requestId,
        Instant timestamp,
        Map<String, String> details
) {

    public ErrorResponse {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static ErrorResponse of(String code, String message, Map<String, String> details) {
        return new ErrorResponse(code, message, RequestContext.currentRequestId(), Instant.now(), details);
    }
}
