package com.relayops.agent.adapter.out.ai;

import com.relayops.agent.application.model.ModelRouter;
import com.relayops.agent.domain.model.ModelProfile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

/**
 * Builds and caches one {@link ChatClient} per configured model slot.
 *
 * <p>Clients are built lazily so a deployment without any model configured never touches the OpenAI
 * classes at runtime, and {@link #chain()} keeps the primary/fallback order for callers that want to
 * retry on failure.</p>
 */
@Component
public class ChatClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatClientRegistry.class);

    private final ModelRouter router;
    private final Map<String, ChatClient> cache = new ConcurrentHashMap<>();

    public ChatClientRegistry(ModelRouter router) {
        this.router = router;
    }

    /** Primary client, or empty when no model is usable (command-only mode). */
    public Optional<ChatClient> client() {
        return router.resolve().map(this::clientFor);
    }

    /** Primary then fallback, ready for retry-style fallback at the call site. */
    public List<ChatClient> chain() {
        return router.chain().stream().map(this::clientFor).toList();
    }

    public Optional<ChatClient> client(String profileId) {
        return router.chain().stream()
                .filter(profile -> profile.id().equals(profileId))
                .findFirst()
                .map(this::clientFor);
    }

    public boolean available() {
        return router.available();
    }

    /** Drops cached clients; call after changing model settings. */
    public void invalidate() {
        cache.clear();
    }

    private ChatClient clientFor(ModelProfile profile) {
        return cache.computeIfAbsent(profile.id(), id -> {
            log.info("初始化聊天模型客户端: slot={}, model={}, baseUrl={}", id, profile.model(), profile.baseUrl());
            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(profile.baseUrl())
                    .apiKey(profile.apiKey())
                    .build();
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(profile.model())
                    .temperature(profile.temperature())
                    .maxTokens(profile.maxTokens())
                    .build();
            OpenAiChatModel model = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(options)
                    .build();
            return ChatClient.builder(model).build();
        });
    }
}
