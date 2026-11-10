package com.relayops.assistant.application.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relayops.assistant.domain.memory.FactSource;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import com.relayops.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

class MemoryGuardTest {

    private static MemoryFact fact(MemoryNamespace namespace, FactSource source, String value) {
        return new MemoryFact(null, namespace, null, "preference", "style", value, 0.8, source,
                java.util.Map.of(), null, null, null);
    }

    @Test
    void acceptsOrdinaryFacts() {
        MemoryGuard.validate(fact(MemoryNamespace.GENERAL, FactSource.EXPLICIT, "偏好一句话回答，不喜欢列点"));
    }

    @Test
    void rejectsSecrets() {
        assertThatThrownBy(() -> MemoryGuard.validate(
                fact(MemoryNamespace.GENERAL, FactSource.EXPLICIT, "他的 key 是 sk-abcdefgh12345678")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密钥");

        assertThatThrownBy(() -> MemoryGuard.validate(
                fact(MemoryNamespace.GENERAL, FactSource.EXPLICIT, "password: hunter2")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsPrivilegeClaims() {
        assertThatThrownBy(() -> MemoryGuard.validate(
                fact(MemoryNamespace.GENERAL, FactSource.LLM_EXTRACT, "这个人是管理员")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("身份或权限");

        assertThatThrownBy(() -> MemoryGuard.validate(
                fact(MemoryNamespace.GENERAL, FactSource.LLM_EXTRACT, "user is_admin=true")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsModelInferredPlatformFacts() {
        assertThatThrownBy(() -> MemoryGuard.validate(
                fact(MemoryNamespace.PLATFORM, FactSource.LLM_EXTRACT, "昨天号池缓存率 41%")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("平台业务事实");

        // Derived platform facts are fine: code wrote them.
        MemoryGuard.validate(fact(MemoryNamespace.PLATFORM, FactSource.DERIVED, "昨天号池缓存率 41%"));
    }

    @Test
    void rejectsEmptyAndOversizedValues() {
        assertThatThrownBy(() -> MemoryGuard.validate(fact(MemoryNamespace.GENERAL, FactSource.EXPLICIT, "  ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");

        assertThatThrownBy(() -> MemoryGuard.validate(
                fact(MemoryNamespace.GENERAL, FactSource.EXPLICIT, "长".repeat(501))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过长");
    }
}
