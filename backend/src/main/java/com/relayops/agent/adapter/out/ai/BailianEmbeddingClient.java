package com.relayops.agent.adapter.out.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relayops.agent.config.EmbeddingProperties;
import com.relayops.agent.contract.EmbeddingClient;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Bailian embedding over the OpenAI-compatible endpoint.
 *
 * <p>Failures and dimension mismatches degrade to {@link Optional#empty()} instead of throwing: a
 * missing embedding only costs recall quality, it must never break a chat turn.</p>
 */
@Component
public class BailianEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(BailianEmbeddingClient.class);

    private final EmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public BailianEmbeddingClient(EmbeddingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = properties.usable()
                ? RestClient.builder().baseUrl(properties.baseUrl()).build()
                : null;
        if (!properties.usable()) {
            log.info("向量化未启用或缺少 api-key，记忆召回将退回关键词模式");
        }
    }

    @Override
    public boolean available() {
        return restClient != null;
    }

    @Override
    public int dimensions() {
        return properties.dimensions();
    }

    @Override
    public String model() {
        return properties.model();
    }

    @Override
    public Optional<float[]> embed(String text) {
        if (restClient == null || text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            String body = requestBody(text);
            String response = restClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseEmbedding(objectMapper.readTree(response == null ? "{}" : response),
                    properties.dimensions(), properties.model());
        } catch (Exception ex) {
            log.warn("向量化失败，本次召回退回关键词模式: model={}, reason={}",
                    properties.model(), ex.getMessage());
            return Optional.empty();
        }
    }

    private String requestBody(String text) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.model());
        root.putArray("input").add(text);
        root.put("dimensions", properties.dimensions());
        return objectMapper.writeValueAsString(root);
    }

    /** Extracts the first embedding and validates its dimension. */
    static Optional<float[]> parseEmbedding(JsonNode response, int expectedDimensions, String model) {
        JsonNode data = response.path("data");
        if (!data.isArray() || data.isEmpty()) {
            log.warn("向量化响应缺少 data: model={}", model);
            return Optional.empty();
        }
        JsonNode values = data.get(0).path("embedding");
        if (!values.isArray() || values.isEmpty()) {
            log.warn("向量化响应缺少 embedding: model={}", model);
            return Optional.empty();
        }
        if (values.size() != expectedDimensions) {
            log.warn("向量维度与配置不一致，已丢弃: model={}, expected={}, actual={}",
                    model, expectedDimensions, values.size());
            return Optional.empty();
        }
        float[] vector = new float[values.size()];
        for (int index = 0; index < values.size(); index++) {
            vector[index] = (float) values.get(index).asDouble();
        }
        return Optional.of(vector);
    }
}
