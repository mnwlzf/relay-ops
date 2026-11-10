package com.relayops.agent.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Embedding settings (Alibaba Bailian, OpenAI-compatible {@code /embeddings}).
 *
 * <p>The dimension is part of the contract with the model: it is configured once and verified against
 * the response, never written into the schema, so switching models only means re-embedding.</p>
 */
@ConfigurationProperties(prefix = "relay-ops.ai.embedding")
public record EmbeddingProperties(
        Boolean enabled,
        String baseUrl,
        String workspaceId,
        String apiKey,
        String model,
        int dimensions,
        int batchSize,
        Duration timeout
) {

    public EmbeddingProperties {
        enabled = enabled == null || enabled;
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://dashscope.aliyuncs.com/compatible-mode/v1"
                : baseUrl.strip();
        apiKey = apiKey == null ? "" : apiKey.strip();
        model = model == null || model.isBlank() ? "text-embedding-v4" : model.strip();
        dimensions = dimensions <= 0 ? 1024 : dimensions;
        batchSize = batchSize <= 0 ? 16 : Math.min(batchSize, 64);
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    }

    /** Whether calls can be attempted at all. */
    public boolean usable() {
        return enabled && !apiKey.isBlank();
    }
}
