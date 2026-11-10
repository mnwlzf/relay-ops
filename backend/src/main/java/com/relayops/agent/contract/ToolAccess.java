package com.relayops.agent.contract;

import java.util.function.Predicate;

/**
 * Permission predicate of a tool.
 *
 * <p>Always evaluated by the kernel on the per-request context; a capability pack or a plugin may
 * declare a predicate but never bypass it.</p>
 */
@FunctionalInterface
public interface ToolAccess extends Predicate<ToolContext> {

    @Override
    boolean test(ToolContext context);

    default boolean allows(ToolContext context) {
        return test(context == null ? ToolContext.anonymous() : context);
    }

    static ToolAccess everyone() {
        return context -> true;
    }

    static ToolAccess adminsOnly() {
        return ToolContext::admin;
    }

    static ToolAccess platformUsers() {
        return ToolContext::platformUser;
    }

    /** Only the sender's own data: the tool itself must not accept a "whose data" argument. */
    static ToolAccess selfOnly() {
        return context -> context.platformUser() && !context.userKey().isEmpty();
    }
}
