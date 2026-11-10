package com.relayops.assistant.application.memory;

import com.relayops.assistant.domain.memory.FactSource;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import com.relayops.shared.error.BusinessException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Refuses to store facts that must never live in memory.
 *
 * <p>Three classes are rejected outright: secrets (keys, passwords, tokens), privilege claims
 * ("X is admin") because identity is resolved per request, and platform facts inferred by the model
 * rather than computed by code.</p>
 */
public final class MemoryGuard {

    private static final int MAX_VALUE_LENGTH = 500;

    private static final Pattern SECRET = Pattern.compile(
            "(?i)(sk-[a-z0-9_-]{8,}|api[_-]?key\\s*[:=]|password\\s*[:=]|passwd\\s*[:=]"
                    + "|token\\s*[:=]|bearer\\s+[a-z0-9._-]{8,}|-----begin [a-z ]*private key)");

    private static final List<String> PRIVILEGE_CLAIMS = List.of(
            "is_admin", "isadmin", "是管理员", "管理员权限", "root权限", "有权限");

    private MemoryGuard() {
    }

    public static void validate(MemoryFact fact) {
        if (fact == null) {
            throw invalid("记忆内容为空");
        }
        if (fact.value().isEmpty()) {
            throw invalid("记忆内容不能为空");
        }
        if (fact.value().length() > MAX_VALUE_LENGTH) {
            throw invalid("记忆内容过长（上限 " + MAX_VALUE_LENGTH + " 字）");
        }
        if (SECRET.matcher(fact.value()).find()) {
            throw invalid("记忆内容疑似包含密钥或口令，已拒绝写入");
        }
        String normalized = fact.value().toLowerCase(java.util.Locale.ROOT);
        for (String claim : PRIVILEGE_CLAIMS) {
            if (normalized.contains(claim)) {
                throw invalid("记忆不允许保存身份或权限结论，身份每次实时判定");
            }
        }
        if (fact.namespace() == MemoryNamespace.PLATFORM && fact.source() == FactSource.LLM_EXTRACT) {
            throw invalid("平台业务事实只能由代码或人工写入，不接受模型推断");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("MEMORY_REJECTED", message);
    }
}
