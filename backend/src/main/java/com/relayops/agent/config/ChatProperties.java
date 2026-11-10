package com.relayops.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chat model settings.
 *
 * <p>Two slots only: a primary and an optional fallback. That covers "main model is down, use the
 * backup" without inventing a profile registry before it is needed; more providers can be added as
 * additional slots later.</p>
 *
 * @param enabled  whether natural-language chat is enabled at all; when false the assistant runs in
 *                 command-only mode
 * @param primary  main model endpoint
 * @param fallback backup model endpoint, used when the primary is not usable
 */
@ConfigurationProperties(prefix = "relay-ops.ai.chat")
public record ChatProperties(Boolean enabled, Profile primary, Profile fallback) {

    public ChatProperties {
        enabled = enabled == null || enabled;
        primary = primary == null ? Profile.empty() : primary;
        fallback = fallback == null ? Profile.empty() : fallback;
    }

    public boolean chatEnabled() {
        return enabled;
    }

    /**
     * One OpenAI-compatible endpoint.
     *
     * @param apiKey    secret; blank means "not configured"
     * @param maxTokens response cap, protects the budget
     */
    public record Profile(String baseUrl, String apiKey, String model, Double temperature, Integer maxTokens) {

        public Profile {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.strip();
            apiKey = apiKey == null ? "" : apiKey.strip();
            model = model == null || model.isBlank() ? null : model.strip();
            temperature = temperature == null ? 0.2 : Math.max(0.0, Math.min(temperature, 2.0));
            maxTokens = maxTokens == null || maxTokens <= 0 ? 1024 : maxTokens;
        }

        public static Profile empty() {
            return new Profile(null, "", null, 0.2, 1024);
        }

        /** Whether this slot has everything needed to build a client. */
        public boolean usable() {
            return baseUrl != null && !apiKey.isBlank() && model != null;
        }
    }
}
