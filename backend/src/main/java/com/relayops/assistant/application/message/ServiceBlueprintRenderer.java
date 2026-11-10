package com.relayops.assistant.application.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.domain.message.Blueprint;
import org.springframework.stereotype.Component;

/** Adapter that binds {@link BlueprintRenderer} to the blueprint store and the composer. */
@Component
public class ServiceBlueprintRenderer implements BlueprintRenderer {

    private final BlueprintService blueprints;
    private final MessageComposer composer;

    public ServiceBlueprintRenderer(BlueprintService blueprints, MessageComposer composer) {
        this.blueprints = blueprints;
        this.composer = composer;
    }

    @Override
    public String render(String code, ScopeRef scope, JsonNode args) {
        Blueprint blueprint = blueprints.byCode(code);
        return composer.compose(blueprint, scope, args == null ? JsonNodeFactory.instance.objectNode() : args).text();
    }
}
