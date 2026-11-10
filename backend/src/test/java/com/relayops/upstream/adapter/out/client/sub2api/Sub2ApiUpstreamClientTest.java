package com.relayops.upstream.adapter.out.client.sub2api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.shared.error.BusinessException;
import com.relayops.upstream.application.collection.UpstreamClient;
import com.relayops.upstream.domain.CollectStatus;
import org.junit.jupiter.api.Test;

class Sub2ApiUpstreamClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void parsesSnakeCasePayloadUnderData() {
        UpstreamClient.SnapshotPayload payload = Sub2ApiUpstreamClient.parse("""
                {"data":{"balance":12.5,"used_quota":99.5,"request_count":17}}
                """, JSON);

        assertThat(payload.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(payload.balance()).isEqualByComparingTo("12.5");
        assertThat(payload.usedQuota()).isEqualByComparingTo("99.5");
        assertThat(payload.requestCount()).isEqualTo(17L);
    }

    @Test
    void parsesCamelCasePayloadAtTheRoot() {
        UpstreamClient.SnapshotPayload payload = Sub2ApiUpstreamClient.parse("""
                {"balance":"3.25","usedQuota":"8","requests":5}
                """, JSON);

        assertThat(payload.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(payload.balance()).isEqualByComparingTo("3.25");
        assertThat(payload.usedQuota()).isEqualByComparingTo("8");
        assertThat(payload.requestCount()).isEqualTo(5L);
    }

    @Test
    void unknownShapeIsPartialAndKeepsRaw() {
        UpstreamClient.SnapshotPayload payload = Sub2ApiUpstreamClient.parse(
                "{\"data\":{\"foo\":1}}", JSON);

        assertThat(payload.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(payload.raw()).contains("foo");
        assertThat(payload.message()).contains("未识别");
    }

    @Test
    void rejectsFailures() {
        assertThatThrownBy(() -> Sub2ApiUpstreamClient.parse(
                "{\"success\":false,\"message\":\"无效的令牌\"}", JSON))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的令牌");

        assertThatThrownBy(() -> Sub2ApiUpstreamClient.parse("", JSON))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("空响应");
    }
}
