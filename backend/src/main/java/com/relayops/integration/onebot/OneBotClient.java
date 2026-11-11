package com.relayops.integration.onebot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends messages back through the OneBot HTTP API (NapCat).
 *
 * <p>Long messages are split by {@link MessageChunker}, each chunk is retried on failure, and sends
 * to the same target are throttled independently by {@link SendThrottle}. A partially delivered
 * report is still better than a silently dropped one, so a failing chunk never aborts the rest.</p>
 */
@Component
public class OneBotClient {

    private static final Logger log = LoggerFactory.getLogger(OneBotClient.class);

    private final OneBotProperties properties;
    private final ObjectMapper objectMapper;
    private final SendThrottle throttle;

    public OneBotClient(OneBotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.throttle = new SendThrottle(properties.sendInterval());
    }

    public boolean available() {
        return properties.usable();
    }

    public void sendGroup(long groupId, String text) {
        sendChunks("group_id", groupId, "/send_group_msg", text);
    }

    public void sendPrivate(long userId, String text) {
        sendChunks("user_id", userId, "/send_private_msg", text);
    }

    private void sendChunks(String idField, long id, String path, String text) {
        if (!available()) {
            log.debug("OneBot 未启用，跳过发送: path={}", path);
            return;
        }
        List<String> chunks = MessageChunker.split(text,
                properties.maxMessageChars(), properties.maxSegments());
        String target = path + ":" + id;
        int delivered = 0;
        for (String chunk : chunks) {
            throttle.await(target);
            if (postWithRetry(path, idField, id, chunk)) {
                delivered++;
            }
        }
        if (chunks.size() > 1) {
            log.info("长消息分片发送: path={}, id={}, segments={}, delivered={}",
                    path, id, chunks.size(), delivered);
        }
        if (delivered < chunks.size()) {
            log.warn("部分分片未送达: path={}, id={}, delivered={}/{}",
                    path, id, delivered, chunks.size());
        }
    }

    private boolean postWithRetry(String path, String idField, long id, String text) {
        int attempts = Math.max(1, properties.retryMax() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            if (post(path, idField, id, text)) {
                return true;
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep(Math.min(1000L, 200L * attempt));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private boolean post(String path, String idField, long id, String text) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put(idField, id);
            body.put("message", text);
            body.put("auto_escape", true);

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) properties.timeout().toMillis());
            factory.setReadTimeout((int) properties.timeout().toMillis());
            RestClient client = RestClient.builder()
                    .baseUrl(trimSlash(properties.apiBaseUrl()))
                    .requestFactory(factory)
                    .build();
            var request = client.post().uri(path).contentType(MediaType.APPLICATION_JSON);
            if (!properties.apiToken().isBlank()) {
                request = request.header("Authorization", "Bearer " + properties.apiToken());
            }
            request.body(objectMapper.writeValueAsString(body)).retrieve().toBodilessEntity();
            return true;
        } catch (Exception ex) {
            // Delivery failures must never break the caller; the message is already recorded locally.
            log.warn("OneBot 发送失败: path={}, id={}, reason={}", path, id, ex.getMessage());
            return false;
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
