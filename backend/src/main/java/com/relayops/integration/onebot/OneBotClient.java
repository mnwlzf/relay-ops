package com.relayops.integration.onebot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends messages back through the OneBot HTTP API (NapCat).
 *
 * <p>Long messages are split by {@link MessageChunker} and sent with a minimum gap between calls:
 * QQ rejects over-long payloads and rate-limits bursts, and a partially delivered report is still
 * better than a silently dropped one.</p>
 */
@Component
public class OneBotClient {

    private static final Logger log = LoggerFactory.getLogger(OneBotClient.class);

    private final OneBotProperties properties;
    private final ObjectMapper objectMapper;
    private final AtomicLong lastSendAt = new AtomicLong(0);

    public OneBotClient(OneBotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
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
        for (int index = 0; index < chunks.size(); index++) {
            if (index > 0) {
                awaitSendSlot();
            }
            post(path, idField, id, chunks.get(index));
        }
        if (chunks.size() > 1) {
            log.info("长消息已分片发送: path={}, id={}, segments={}", path, id, chunks.size());
        }
    }

    /** Keeps a minimum gap between two outbound messages so the QQ account is not throttled. */
    private void awaitSendSlot() {
        long interval = properties.sendInterval().toMillis();
        if (interval <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long earliest = lastSendAt.get() + interval;
        if (now < earliest) {
            try {
                Thread.sleep(Math.min(interval, earliest - now));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        lastSendAt.set(System.currentTimeMillis());
    }

    private void post(String path, String idField, long id, String text) {
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
        } catch (Exception ex) {
            // Delivery failures must never break the caller; the message is already recorded locally.
            log.warn("OneBot 发送失败: path={}, id={}, reason={}", path, id, ex.getMessage());
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
