package com.relayops.assistant.domain.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.List;

/**
 * A scheduled message: an ordered list of segments plus execution options.
 *
 * <p>Segments are executed in order; a {@code tool} segment stores its result under {@code as} so
 * later segments and text templates can reference it.</p>
 */
public record Blueprint(
        Long id,
        String code,
        String name,
        String packId,
        Long personaId,
        List<Segment> segments,
        Options options,
        boolean enabled,
        int version
) {

    public Blueprint {
        segments = segments == null ? List.of() : List.copyOf(segments);
        options = options == null ? Options.defaults() : options;
        version = Math.max(version, 1);
    }

    /** One segment of a blueprint. */
    public sealed interface Segment permits TextSegment, ToolSegment, PersonaNoteSegment {

        String type();
    }

    /** Literal text with {@code {{...}}} placeholders. */
    public record TextSegment(String template) implements Segment {

        @Override
        public String type() {
            return "text";
        }
    }

    /**
     * Read-only tool invocation.
     *
     * @param ref    tool id, e.g. {@code general.clock}
     * @param as     namespace for later references; defaults to {@code ref}
     * @param args   arguments, may themselves contain placeholders
     * @param title  optional heading rendered before the result
     * @param onError {@code SKIP} / {@code DEGRADE} / {@code ABORT}
     */
    public record ToolSegment(String ref, String as, JsonNode args, String title,
                              String onError, String onEmpty) implements Segment {

        public ToolSegment {
            args = args == null ? JsonNodeFactory.instance.objectNode() : args;
            onError = onError == null || onError.isBlank() ? "DEGRADE" : onError.strip().toUpperCase(java.util.Locale.ROOT);
            onEmpty = onEmpty == null || onEmpty.isBlank() ? "HIDE" : onEmpty.strip().toUpperCase(java.util.Locale.ROOT);
        }

        public String namespace() {
            return as == null || as.isBlank() ? ref : as.strip();
        }

        @Override
        public String type() {
            return "tool";
        }
    }

    /** A short model-written commentary; skipped when no model is configured. */
    public record PersonaNoteSegment(String prompt) implements Segment {

        @Override
        public String type() {
            return "persona_note";
        }
    }

    /**
     * Execution options.
     *
     * @param maxToolCalls  guard against a blueprint that fans out to too many upstream calls
     * @param onToolError   default failure policy for tool segments
     * @param fallback      {@code TEXT} keeps the message when rendering degrades
     */
    public record Options(Integer maxToolCalls, String onToolError, String fallback) {

        public Options {
            maxToolCalls = maxToolCalls == null || maxToolCalls <= 0 ? 8 : Math.min(maxToolCalls, 20);
            onToolError = onToolError == null || onToolError.isBlank()
                    ? "DEGRADE" : onToolError.strip().toUpperCase(java.util.Locale.ROOT);
            fallback = fallback == null || fallback.isBlank() ? "TEXT" : fallback.strip().toUpperCase(java.util.Locale.ROOT);
        }

        public static Options defaults() {
            return new Options(8, "DEGRADE", "TEXT");
        }
    }
}
