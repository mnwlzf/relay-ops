package com.relayops.assistant.adapter.out.ai;

import com.relayops.agent.adapter.out.ai.ChatClientRegistry;
import com.relayops.assistant.application.memory.TextSummarizer;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Summarises via the resolved chat model.
 *
 * <p>Any failure is swallowed and reported as "no summary": a missing summary only shortens memory,
 * it must not break the maintenance task.</p>
 */
@Component
public class ChatTextSummarizer implements TextSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ChatTextSummarizer.class);

    private final ChatClientRegistry registry;

    public ChatTextSummarizer(ChatClientRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean available() {
        return registry.available();
    }

    @Override
    public Optional<String> summarize(String prompt) {
        if (!registry.available()) {
            return Optional.empty();
        }
        try {
            String content = registry.client()
                    .map(client -> client.prompt().user(prompt).call().content())
                    .orElse(null);
            return content == null || content.isBlank() ? Optional.empty() : Optional.of(content.strip());
        } catch (RuntimeException ex) {
            log.warn("生成摘要失败，本次跳过: reason={}", ex.getMessage());
            return Optional.empty();
        }
    }
}
