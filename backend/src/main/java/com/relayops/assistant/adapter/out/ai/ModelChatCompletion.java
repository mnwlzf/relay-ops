package com.relayops.assistant.adapter.out.ai;

import com.relayops.agent.adapter.out.ai.ChatClientRegistry;
import com.relayops.assistant.application.chat.ChatCompletion;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Chat completion through the resolved model slot (primary, then fallback). */
@Component
public class ModelChatCompletion implements ChatCompletion {

    private static final Logger log = LoggerFactory.getLogger(ModelChatCompletion.class);

    private final ChatClientRegistry registry;

    public ModelChatCompletion(ChatClientRegistry registry) {
        this.registry = registry;
    }

    @Override
    public boolean available() {
        return registry.available();
    }

    @Override
    public Optional<String> complete(String systemPrompt, String userMessage) {
        if (!registry.available()) {
            return Optional.empty();
        }
        try {
            return registry.client()
                    .map(client -> client.prompt().system(systemPrompt).user(userMessage).call().content())
                    .filter(text -> text != null && !text.isBlank())
                    .map(String::strip);
        } catch (RuntimeException ex) {
            log.warn("模型调用失败，按无模型处理: reason={}", ex.getMessage());
            return Optional.empty();
        }
    }
}
