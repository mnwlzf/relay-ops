package com.relayops.assistant.application.persona;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.domain.ProfileBinding;
import java.util.List;
import java.util.Optional;

/**
 * Picks the persona id for a request scope.
 *
 * <p>Order: the exact scope (group / channel / user) first, then the global default binding. A
 * binding that is disabled, points to a missing persona or belongs to another pack is ignored, so a
 * broken binding degrades to the wider scope instead of failing the request.</p>
 */
public final class PersonaResolver {

    private PersonaResolver() {
    }

    public static Optional<Long> resolve(ScopeRef scope, String packId, List<ProfileBinding> bindings) {
        if (scope == null || packId == null || bindings == null || bindings.isEmpty()) {
            return Optional.empty();
        }
        List<ScopeRef> chain = List.of(scope, ScopeRef.global());
        for (ScopeRef candidate : chain) {
            Optional<Long> found = bindings.stream()
                    .filter(binding -> binding.matches(candidate, packId))
                    .map(ProfileBinding::personaId)
                    .filter(java.util.Objects::nonNull)
                    .findFirst();
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
