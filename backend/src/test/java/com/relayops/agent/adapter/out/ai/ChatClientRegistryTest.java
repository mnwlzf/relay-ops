package com.relayops.agent.adapter.out.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.agent.application.model.ModelRouter;
import com.relayops.agent.config.ChatProperties;
import org.junit.jupiter.api.Test;

class ChatClientRegistryTest {

    private static ChatProperties.Profile profile(String apiKey) {
        return new ChatProperties.Profile("https://api.example.com", apiKey, "gpt-4o-mini", 0.2, 512);
    }

    @Test
    void withoutConfigurationNoClientIsBuilt() {
        ChatClientRegistry registry = new ChatClientRegistry(
                new ModelRouter(new ChatProperties(true, ChatProperties.Profile.empty(),
                        ChatProperties.Profile.empty())));

        assertThat(registry.available()).isFalse();
        assertThat(registry.client()).isEmpty();
        assertThat(registry.chain()).isEmpty();
    }

    @Test
    void buildsOneClientPerConfiguredSlot() {
        ChatClientRegistry registry = new ChatClientRegistry(
                new ModelRouter(new ChatProperties(true, profile("sk-primary-123"),
                        new ChatProperties.Profile("https://backup.example.com", "sk-backup-123",
                                "qwen-max", 0.1, 256))));

        assertThat(registry.available()).isTrue();
        assertThat(registry.client()).isPresent();
        assertThat(registry.chain()).hasSize(2);
        assertThat(registry.client("fallback")).isPresent();
        assertThat(registry.client("missing")).isEmpty();
    }
}
