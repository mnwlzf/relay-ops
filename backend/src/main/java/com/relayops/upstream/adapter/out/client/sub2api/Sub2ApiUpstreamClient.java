package com.relayops.upstream.adapter.out.client.sub2api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.shared.error.BusinessException;
import com.relayops.upstream.application.collection.UpstreamClient;
import com.relayops.upstream.domain.PlatformType;
import com.relayops.upstream.domain.UpstreamAccount;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sub2API collector.
 *
 * <p>Reads {@code GET /v1/usage} with a bearer token. Sub2API deployments differ in where the numbers
 * live, so parsing accepts a few common shapes ({@code data.*} or root, snake_case or camelCase) and
 * reports PARTIAL with the raw payload when nothing matches. That keeps a shape mismatch visible
 * instead of silently writing zeroes.</p>
 */
@Component
public class Sub2ApiUpstreamClient implements UpstreamClient {

    private static final Logger log = LoggerFactory.getLogger(Sub2ApiUpstreamClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final List<String> BALANCE_KEYS = List.of("balance", "quota", "remain_quota");
    private static final List<String> USED_KEYS = List.of("used_quota", "usedQuota", "used", "total_used");
    private static final List<String> REQUEST_KEYS = List.of("request_count", "requestCount", "requests", "count");

    private final ObjectMapper objectMapper;

    public Sub2ApiUpstreamClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public PlatformType type() {
        return PlatformType.SUB2API;
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
                    .uri("/v1/usage")
                    .header("Authorization", "Bearer " + credential)
                    .retrieve()
                    .body(String.class);
            return parse(body, objectMapper);
        } catch (Exception ex) {
            log.warn("Sub2API 采集失败: account={}, reason={}", account.login(), ex.getMessage());
            throw new BusinessException("COLLECT_FAILED", "采集失败: " + ex.getMessage(), ex);
        }
    }

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
            throw new BusinessException("COLLECT_REJECTED",
                    root.path("message").asText("上游返回 success=false"));
        }
        // Sub2API may wrap the payload in data, or return it at the root.
        JsonNode data = root.path("data").isObject() ? root.get("data") : root;
        BigDecimal balance = firstDecimal(data, BALANCE_KEYS);
        BigDecimal used = firstDecimal(data, USED_KEYS);
        Long requests = firstLong(data, REQUEST_KEYS);
        if (balance == null && used == null) {
            return SnapshotPayload.partial(null, null, requests, raw,
                    "未识别到余额/用量字段，已保留原始响应");
        }
        return SnapshotPayload.success(balance, used, requests, raw);
    }

    private static BigDecimal firstDecimal(JsonNode node, List<String> keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText().trim());
            }
        }
        return null;
    }

    private static Long firstLong(JsonNode node, List<String> keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                return value.isNumber() ? value.asLong() : Long.parseLong(value.asText().trim());
            }
        }
        return null;
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
