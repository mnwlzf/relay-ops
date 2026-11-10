package com.relayops.agent.domain.model;

import com.relayops.agent.config.ChatProperties;

/**
 * A usable model endpoint with an identifier, so logs and the admin page can say which one answered.
 */
public record ModelProfile(String id, String baseUrl, String apiKey, String model,
                           double temperature, int maxTokens) {

    public static final String PRIMARY = "primary";
    public static final String FALLBACK = "fallback";

    public static ModelProfile from(String id, ChatProperties.Profile profile) {
        return new ModelProfile(id, profile.baseUrl(), profile.apiKey(), profile.model(),
                profile.temperature(), profile.maxTokens());
    }

    /** Never log the key; the masked form is safe for status output. */
    public String masked() {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
