package com.relayops.agent.application.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.agent.config.ChatProperties;
import com.relayops.agent.domain.model.ModelProfile;
import org.junit.jupiter.api.Test;

class ModelRouterTest {

    private static ChatProperties properties(boolean enabled, ChatProperties.Profile primary,
                                             ChatProperties.Profile fallback) {
        return new ChatProperties(enabled, primary, fallback);
    }

    private static ChatProperties.Profile profile(String baseUrl, String apiKey, String model) {
        return new ChatProperties.Profile(baseUrl, apiKey, model, 0.2, 1024);
    }

    @Test
    void disabledChatYieldsNoProfilesEvenWhenConfigured() {
        ModelRouter router = new ModelRouter(properties(false,
                profile("https://api.example.com", "sk-1234567890", "gpt-4o-mini"),
                ChatProperties.Profile.empty()));

        assertThat(router.chain()).isEmpty();
        assertThat(router.available()).isFalse();
        assertThat(router.status().reason()).contains("已禁用");
    }

    @Test
    void incompletePrimaryIsSkippedAndFallbackIsUsed() {
        // api key missing on purpose
        ModelRouter router = new ModelRouter(properties(true,
                profile("https://api.example.com", "  ", "gpt-4o-mini"),
                profile("https://backup.example.com", "sk-backup-123", "qwen-max")));

        assertThat(router.chain()).extracting(ModelProfile::id).containsExactly(ModelProfile.FALLBACK);
        assertThat(router.resolve()).map(ModelProfile::model).contains("qwen-max");
        assertThat(router.status().reason()).isEqualTo("就绪");
    }

    @Test
    void primaryWinsAndFallbackStaysInTheChain() {
        ModelRouter router = new ModelRouter(properties(true,
                profile("https://api.example.com", "sk-primary-123", "gpt-4o-mini"),
                profile("https://backup.example.com", "sk-backup-123", "qwen-max")));

        assertThat(router.chain()).extracting(ModelProfile::id)
                .containsExactly(ModelProfile.PRIMARY, ModelProfile.FALLBACK);
        assertThat(router.status().profiles()).hasSize(2);
        assertThat(router.status().profiles().get(0).maskedApiKey()).doesNotContain("primary");
    }

    @Test
    void nothingUsableMeansCommandOnlyMode() {
        ModelRouter router = new ModelRouter(properties(true, ChatProperties.Profile.empty(),
                ChatProperties.Profile.empty()));

        assertThat(router.available()).isFalse();
        assertThat(router.status().enabled()).isTrue();
        assertThat(router.status().reason()).contains("未配置可用模型");
    }

    @Test
    void maskedKeyNeverLeaksTheSecret() {
        ModelProfile profile = ModelProfile.from(ModelProfile.PRIMARY,
                profile("https://api.example.com", "sk-abcdefghijklmnop", "gpt-4o-mini"));

        assertThat(profile.masked()).isEqualTo("sk-a****mnop");
        assertThat(ModelProfile.from(ModelProfile.PRIMARY, ChatProperties.Profile.empty()).masked())
                .isEqualTo("****");
    }
}
