package com.relayops.upstream.adapter.out.client.newapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.shared.error.BusinessException;
import com.relayops.upstream.application.collection.UpstreamClient;
import com.relayops.upstream.domain.PlatformType;
import com.relayops.upstream.domain.UpstreamAccount;
import java.math.BigDecimal;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * New API collector.
 *
 * <p>Reads {@code GET /api/user/self} with the account token plus the {@code New-Api-User} header
 * (New API requires both). Numbers are stored as returned by the platform: New API reports quota in
 * its own unit, and unit conversion is deliberately left to the dashboard layer.</p>
 */
@Component
public class NewApiUpstreamClient implements UpstreamClient {

    private static final Logger log = LoggerFactory.getLogger(NewApiUpstreamClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ObjectMapper objectMapper;

    public NewApiUpstreamClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public PlatformType type() {
        return PlatformType.NEWAPI;
    }

    @Override
    public SnapshotPayload fetch(UpstreamAccount account, String credential) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        RestClient client = RestClient.builder()
                .baseUrl(trimTrailingSlash(account.baseUrl()))
                .requestFactory(factory)
                .build();
        try {
            String body = client.get()
                    .uri("/api/user/self")
                    .header("Authorization", credential)
                    .header("New-Api-User", account.login())
                    .retrieve()
                    .body(String.class);
            return parse(body, objectMapper);
        } catch (Exception ex) {
            log.warn("New API 采集失败: account={}, reason={}", account.login(), ex.getMessage());
            throw new BusinessException("COLLECT_FAILED", "采集失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * Parses {@code {"success":true,"data":{"quota":..,"used_quota":..,"request_count":..}}}.
     *
     * <p>A payload without {@code data} is reported as PARTIAL instead of FAILED: the credential was
     * accepted, the shape just does not match what we expect.</p>
     */
    static SnapshotPayload parse(String body, ObjectMapper objectMapper) {
        String raw = body == null ? "" : body;
        if (raw.isBlank()) {
            throw new BusinessException("COLLECT_EMPTY", "上游返回空响应");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new BusinessException("COLLECT_BAD_JSON", "上游响应不是合法 JSON", ex);
        }
        if (root.path("success").isBoolean() && !root.get("success").asBoolean()) {
            String message = root.path("message").asText("上游返回 success=false");
            throw new BusinessException("COLLECT_REJECTED", message);
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            return SnapshotPayload.partial(null, null, null, raw, "响应缺少 data 字段");
        }
        BigDecimal quota = decimal(data, "quota");
        BigDecimal used = decimal(data, "used_quota");
        Long requests = data.hasNonNull("request_count") ? data.get("request_count").asLong() : null;
        if (quota == null && used == null) {
            return SnapshotPayload.partial(null, null, requests, raw, "响应缺少 quota 与 used_quota");
        }
        return SnapshotPayload.success(quota, used, requests, raw);
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isNumber()
                ? value.decimalValue()
                : new BigDecimal(value.asText().trim());
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
