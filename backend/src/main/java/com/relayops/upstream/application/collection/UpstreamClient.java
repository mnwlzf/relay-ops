package com.relayops.upstream.application.collection;

import com.relayops.upstream.domain.CollectStatus;
import com.relayops.upstream.domain.PlatformType;
import com.relayops.upstream.domain.UpstreamAccount;
import java.math.BigDecimal;

/** Fetches one account snapshot from an upstream platform. */
public interface UpstreamClient {

    PlatformType type();

    /**
     * @param credential plaintext credential, decrypted by the caller for this call only
     * @throws RuntimeException when the platform cannot be reached or rejects the credential
     */
    SnapshotPayload fetch(UpstreamAccount account, String credential);

    /**
     * Parsed snapshot fields.
     *
     * @param raw upstream payload, kept for replay and debugging
     */
    record SnapshotPayload(CollectStatus status, BigDecimal balance, BigDecimal usedQuota,
                           Long requestCount, String raw, String message) {

        public static SnapshotPayload success(BigDecimal balance, BigDecimal usedQuota, Long requestCount,
                                              String raw) {
            return new SnapshotPayload(CollectStatus.SUCCESS, balance, usedQuota, requestCount, raw, null);
        }

        public static SnapshotPayload partial(BigDecimal balance, BigDecimal usedQuota, Long requestCount,
                                              String raw, String message) {
            return new SnapshotPayload(CollectStatus.PARTIAL, balance, usedQuota, requestCount, raw, message);
        }
    }
}
