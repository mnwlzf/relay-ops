package com.relayops.agent.contract;

/** Human readable metadata of a capability pack, shown on the admin pages. */
public record PackDescriptor(String title, String description) {

    public PackDescriptor {
        title = title == null || title.isBlank() ? "未命名能力包" : title.strip();
        description = description == null ? "" : description.strip();
    }
}
