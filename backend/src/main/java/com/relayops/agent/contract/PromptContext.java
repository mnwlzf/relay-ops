package com.relayops.agent.contract;

import com.relayops.agent.domain.persona.PersonaCard;

/** Input of a capability pack's prompt contribution. */
public record PromptContext(
        ScopeRef scope,
        PersonaCard persona,
        boolean admin,
        boolean platformUser
) {

    public PromptContext {
        scope = scope == null ? ScopeRef.global() : scope;
        persona = persona == null ? PersonaCard.neutral() : persona;
    }
}
