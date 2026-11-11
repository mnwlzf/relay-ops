package com.relayops.identity.application;

import com.relayops.identity.domain.IdentityUser;
import java.util.Optional;

/** Read side of the account store, so authentication can be tested without a database. */
public interface UserDirectory {

    Optional<IdentityUser> findByUsername(String username);
}
