package com.relayops.upstream.contract;

import java.util.List;
import java.util.Optional;

/**
 * Cross-module read contract for upstream accounts.
 */
public interface UpstreamQueryPort {

    Optional<AccountSummary> findAccount(long accountId);

    List<AccountSummary> listAccounts(long platformId);

    record AccountSummary(
            long id,
            long platformId,
            String name,
            boolean enabled
    ) {
    }
}
