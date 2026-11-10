package com.relayops.upstream.domain;

/** Upstream platform family; decides which collection client is used. */
public enum PlatformType {

    NEWAPI("New API"),

    SUB2API("Sub2API");

    private final String label;

    PlatformType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static PlatformType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new com.relayops.shared.error.BusinessException(
                    "PLATFORM_TYPE_INVALID", "平台类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new com.relayops.shared.error.BusinessException(
                    "PLATFORM_TYPE_INVALID", "未知的平台类型: " + value);
        }
    }
}
