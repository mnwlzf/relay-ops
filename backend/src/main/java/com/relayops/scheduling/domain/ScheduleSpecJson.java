package com.relayops.scheduling.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** Shared helpers for reading schedule JSON defensively. */
public final class ScheduleSpecJson {

    private ScheduleSpecJson() {
    }

    /** Trimmed text value of a field, or {@code null} when absent/blank. */
    public static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    /**
     * Parses an instant from {@code ISO_INSTANT}, {@code ISO_OFFSET_DATE_TIME} or a local date-time
     * that the caller resolves against its own zone.
     *
     * @return the instant, or {@code null} when the text is a local date-time
     * @throws IllegalArgumentException when the text matches none of the supported formats
     */
    public static Instant instantOrNull(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            // try offset date-time
        }
        try {
            return java.time.OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException ignored) {
            // treat as local date-time for the caller to resolve
        }
        return null;
    }

    /** Whether the text can be parsed as a local date-time. */
    public static boolean looksLikeLocalDateTime(String value) {
        try {
            java.time.LocalDateTime.parse(value);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
