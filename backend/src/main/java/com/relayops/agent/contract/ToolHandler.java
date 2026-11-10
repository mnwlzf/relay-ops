package com.relayops.agent.contract;

/** Executes a {@link ToolSpec}. Implementations must be read-only unless declared otherwise. */
@FunctionalInterface
public interface ToolHandler {

    ToolResult invoke(ToolInvocation invocation) throws Exception;
}
