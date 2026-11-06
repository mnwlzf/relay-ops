package com.relayops.identity.contract;

import java.util.Optional;
import java.util.Set;

/**
 * Read-only identity view for other modules.
 */
public interface CurrentUserQuery {

    Optional<PrincipalView> currentPrincipal();

    record PrincipalView(long id, String username, Set<String> roles) {

        public PrincipalView {
            roles = Set.copyOf(roles);
        }
    }
}
