package com.relayops.agent.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * One tool call as seen by a handler.
 *
 * <p>Identity is injected by the caller (per request), so handlers never take a "query whose data"
 * argument — that is what makes privilege escalation impossible from the model side.</p>
 */
public record ToolInvocation(
        String toolId,
        JsonNode args,
        ScopeRef scope,
        String userKey,
        boolean admin
) {

    public ToolInvocation {
        args = args == null ? JsonNodeFactory.instance.objectNode() : args;
        scope = scope == null ? ScopeRef.global() : scope;
        userKey = userKey == null ? "" : userKey;
    }

    public String stringArg(String field) {
        JsonNode node = args.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    public int intArg(String field, int fallback) {
        JsonNode node = args.get(field);
        return node == null || node.isNull() ? fallback : node.asInt(fallback);
    }
}
