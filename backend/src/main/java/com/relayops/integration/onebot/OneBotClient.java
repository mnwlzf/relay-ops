package com.relayops.integration.onebot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Sends messages back through the OneBot HTTP API (NapCat). */
@Component
public class OneBotClient {

    private static final Logger log = LoggerFactory.getLogger(OneBotClient.class);

    private final OneBotProperties properties;
    private final ObjectMapper objectMapper;

    public OneBotClient(OneBotProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean available() {
        return properties.usable();
    }

    public void sendGroup(long groupId, String text) {
        post("/send_group_msg", "group_id", groupId, text);
    }

    public void sendPrivate(long userId, String text) {
        post("/send_private_msg", "user_id", userId, text);
    }

    private void post(String path, String idField, long id, String text) {
        if (!available()) {
            log.debug("OneBot 未启用，跳过发送: path={}", path);
            return;
        }
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

    static Duration timeout() {
        return Duration.ofSeconds(5);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
