package com.relayops.assistant.application.persona;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.domain.StoredPersona;

/** Read side of persona resolution, so chat logic can be tested without persistence. */
public interface PersonaDirectory {

    StoredPersona resolve(ScopeRef scope, String packId);
}
