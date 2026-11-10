package com.relayops.assistant.application.message;

import com.relayops.agent.contract.ScopeRef;
import com.fasterxml.jackson.databind.JsonNode;

/** Renders a blueprint to text without sending it; used by commands and the admin preview. */
public interface BlueprintRenderer {

    String render(String code, ScopeRef scope, JsonNode args);
}
