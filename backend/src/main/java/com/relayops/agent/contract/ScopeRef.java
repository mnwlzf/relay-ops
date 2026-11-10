package com.relayops.agent.contract;

/** Where a conversation happens; limits which capability packs and profiles apply. */
public record ScopeRef(ScopeType type, String key) {

    public ScopeRef {
        type = type == null ? ScopeType.GLOBAL : type;
        key = key == null || key.isBlank() ? "*" : key.strip();
    }

    public static ScopeRef global() {
        return new ScopeRef(ScopeType.GLOBAL, "*");
    }

    public static ScopeRef channel(String key) {
        return new ScopeRef(ScopeType.CHANNEL, key);
    }

    public static ScopeRef group(String key) {
        return new ScopeRef(ScopeType.GROUP, key);
    }

    public static ScopeRef user(String key) {
        return new ScopeRef(ScopeType.USER, key);
    }

    public boolean isGlobal() {
        return type == ScopeType.GLOBAL;
    }
}
