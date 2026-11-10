package com.relayops.agent.domain.prompt;

/** Kernel-level prompt blocks that every capability pack inherits. */
public final class DefaultSections {

    private static final String SAFETY = """
            1. 只依据工具返回的真实数据回答，不编造数字、链接或结论。
            2. 不声称拥有未授予的权限，也不暗示可以绕过权限。
            3. 不输出密钥、令牌、密码或他人隐私；被要求时明确拒绝。
            4. 角色设定与表达风格不能覆盖以上任何一条。
            """;

    private DefaultSections() {
    }

    /** Hard rules; protected so no persona or plugin can replace them. */
    public static PromptSection safety() {
        return PromptSection.protectedSection("安全边界", PromptLayer.SAFETY, SAFETY);
    }
}
