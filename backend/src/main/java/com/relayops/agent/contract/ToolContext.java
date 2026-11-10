package com.relayops.agent.contract;

import java.util.Set;

/** Input of a capability pack's tool contribution and of every permission predicate. */
public record ToolContext(
        ScopeRef scope,
        String userKey,
        boolean admin,
        boolean platformUser,
        Set<String> permissions
) {

    public ToolContext {
        scope = scope == null ? ScopeRef.global() : scope;
        userKey = userKey == null ? "" : userKey.strip();
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public static ToolContext anonymous() {
        return new ToolContext(ScopeRef.global(), "", false, false, Set.of());
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
