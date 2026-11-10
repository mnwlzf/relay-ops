package com.relayops.upstream.adapter.out.client.newapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.shared.error.BusinessException;
import com.relayops.upstream.application.collection.UpstreamClient;
import com.relayops.upstream.domain.CollectStatus;
import org.junit.jupiter.api.Test;

class NewApiUpstreamClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void parsesQuotaAndUsage() {
        UpstreamClient.SnapshotPayload payload = NewApiUpstreamClient.parse("""
                {"success":true,"data":{"quota":1500.5,"used_quota":4200,"request_count":321}}
                """, JSON);

        assertThat(payload.status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(payload.balance()).isEqualByComparingTo("1500.5");
        assertThat(payload.usedQuota()).isEqualByComparingTo("4200");
        assertThat(payload.requestCount()).isEqualTo(321L);
        assertThat(payload.raw()).contains("\"quota\"");
    }

    @Test
    void reportsPartialWhenDataIsMissing() {
        UpstreamClient.SnapshotPayload payload = NewApiUpstreamClient.parse(
                "{\"success\":true}", JSON);

        assertThat(payload.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(payload.message()).contains("data");
    }

    @Test
    void reportsPartialWhenNeitherQuotaNorUsageIsPresent() {
        UpstreamClient.SnapshotPayload payload = NewApiUpstreamClient.parse(
                "{\"success\":true,\"data\":{\"id\":1}}", JSON);

        assertThat(payload.status()).isEqualTo(CollectStatus.PARTIAL);
        assertThat(payload.message()).contains("quota");
    }

    @Test
    void rejectsUnsuccessfulPayloads() {
        assertThatThrownBy(() -> NewApiUpstreamClient.parse(
                "{\"success\":false,\"message\":\"令牌无效\"}", JSON))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("令牌无效");

        assertThatThrownBy(() -> NewApiUpstreamClient.parse("not-json", JSON))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("合法 JSON");
    }
}
