package com.relayops.assistant.domain;

import com.relayops.agent.contract.ScopeRef;

/** One row of {@code assistant_profile}: which persona a scope uses for a capability pack. */
public record ProfileBinding(Long id, ScopeRef scope, String packId, Long personaId, boolean enabled) {

    public boolean matches(ScopeRef other, String pack) {
        return enabled
                && packId.equals(pack)
                && scope.type() == other.type()
                && scope.key().equals(other.key());
    }
}
