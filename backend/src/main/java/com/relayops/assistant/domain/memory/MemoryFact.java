package com.relayops.assistant.domain.memory;

import com.relayops.agent.contract.ScopeRef;
import java.time.Instant;
import java.util.Map;

/**
 * One long-term fact.
 *
 * @param category   free-form bucket: preference / profile / convention / business
 * @param factKey    optional stable key for updates, e.g. {@code answer_style}
 * @param provenance source reference: message id, task id, operator name
 * @param expiresAt  {@code null} means "until forgotten"
 */
public record MemoryFact(
        Long id,
        MemoryNamespace namespace,
        ScopeRef scope,
        String category,
        String factKey,
        String value,
        double confidence,
        FactSource source,
        Map<String, String> provenance,
        Instant expiresAt,
        Instant lastUsedAt,
        Instant createdAt
) {

    public MemoryFact {
        scope = scope == null ? ScopeRef.global() : scope;
        category = category == null || category.isBlank() ? "general" : category.strip();
        factKey = factKey == null || factKey.isBlank() ? null : factKey.strip();
        value = value == null ? "" : value.strip();
        confidence = Math.max(0.0, Math.min(confidence, 1.0));
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
    }

    public boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /** Text handed to the prompt; keeps the key so the model can reason about what it is. */
    public String asPromptLine() {
        return factKey == null ? value : factKey + "：" + value;
    }
}
