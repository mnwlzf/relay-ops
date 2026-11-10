package com.relayops.assistant.domain;

import com.relayops.agent.domain.persona.PersonaCard;

/**
 * A persona as stored: the kernel card plus the persistence id used by bindings and by memory scopes.
 */
public record StoredPersona(Long id, PersonaCard card) {

    public static StoredPersona transientCard(PersonaCard card) {
        return new StoredPersona(null, card);
    }

    public int version() {
        return card.version();
    }

    public boolean persisted() {
        return id != null;
    }
}
