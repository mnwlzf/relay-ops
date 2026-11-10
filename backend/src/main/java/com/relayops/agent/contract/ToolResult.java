package com.relayops.agent.contract;

/** Result of a tool call, already carrying the data the model or a message segment needs. */
public record ToolResult(boolean success, com.fasterxml.jackson.databind.JsonNode data, String text, String error) {

    public static ToolResult ok(com.fasterxml.jackson.databind.JsonNode data) {
        return new ToolResult(true, data, null, null);
    }

    public static ToolResult text(String text) {
        return new ToolResult(true, null, text, null);
    }

    public static ToolResult failed(String error) {
        return new ToolResult(false, null, null, error == null ? "未知错误" : error);
    }

    /** Payload for a message segment: structured data first, plain text as fallback. */
    public com.fasterxml.jackson.databind.JsonNode payload() {
        return data;
    }
}
