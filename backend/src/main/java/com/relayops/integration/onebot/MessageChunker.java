package com.relayops.integration.onebot;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits long messages into QQ-sized chunks.
 *
 * <p>QQ rejects over-long messages, and a silently dropped daily report is worse than three short
 * ones. Splitting prefers a newline boundary so tables and lists stay readable, falls back to a
 * space, and only then hard-cuts. The number of chunks is capped so a runaway report cannot flood
 * the group.</p>
 */
public final class MessageChunker {

    /** Appended to the last chunk when the content was truncated. */
    static final String TRUNCATED_SUFFIX = "\n…（内容过长，已截断）";

    private MessageChunker() {
    }

    /**
     * @param maxChars    soft limit per chunk; values below 50 are raised to 50
     * @param maxSegments hard cap on chunk count; values below 1 are raised to 1
     * @return chunks in order; empty when the text is blank
     */
    public static List<String> split(String text, int maxChars, int maxSegments) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int limit = Math.max(50, maxChars);
        int segments = Math.max(1, maxSegments);
        String remaining = text.strip();
        List<String> chunks = new ArrayList<>();

        while (!remaining.isEmpty() && chunks.size() < segments) {
            if (remaining.length() <= limit) {
                chunks.add(remaining);
                remaining = "";
                break;
            }
            int cut = bestCut(remaining, limit);
            chunks.add(remaining.substring(0, cut).strip());
            remaining = remaining.substring(cut).strip();
        }

        if (!remaining.isEmpty()) {
            int last = chunks.size() - 1;
            chunks.set(last, chunks.get(last) + TRUNCATED_SUFFIX);
        }
        return List.copyOf(chunks);
    }

    /** Prefers the last newline, then the last space, inside the second half of the window. */
    private static int bestCut(String text, int limit) {
        int floor = limit / 2;
        int newline = text.lastIndexOf('\n', limit - 1);
        if (newline >= floor) {
            return newline + 1;
        }
        int space = text.lastIndexOf(' ', limit - 1);
        if (space >= floor) {
            return space + 1;
        }
        return limit;
    }
}
