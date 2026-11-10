package com.relayops.upstream.application.collection;

import com.relayops.shared.error.BusinessException;
import com.relayops.shared.time.BusinessTime;
import com.relayops.upstream.application.security.CredentialCipher;
import com.relayops.upstream.domain.AccountSnapshot;
import com.relayops.upstream.domain.CollectStatus;
import com.relayops.upstream.domain.UpstreamAccount;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Collection orchestration: one row per account per run, plus one run record.
 *
 * <p>A failing account never aborts the run; its failure is stored on the snapshot and on the
 * account row so the dashboard can show "which account is broken" without reading logs.</p>
 */
@Service
public class CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionService.class);

    private final UpstreamStore store;
    private final CredentialCipher cipher;
    private final List<UpstreamClient> clients;
    private final BusinessTime time;

    public CollectionService(UpstreamStore store, CredentialCipher cipher,
                             List<UpstreamClient> clients, BusinessTime time) {
        this.store = store;
        this.cipher = cipher;
        this.clients = List.copyOf(clients);
        this.time = time;
    }

    public Summary collectAll(String triggerSource) {
        Instant startedAt = time.now();
        long runId = store.startRun(triggerSource, startedAt);
        List<UpstreamAccount> accounts = store.enabledAccounts();
        int succeeded = 0;
        int failed = 0;
        for (UpstreamAccount account : accounts) {
            AccountSnapshot snapshot = collectOne(account);
            store.insertSnapshot(snapshot);
            store.updateAccountStatus(account.id(), snapshot.collectedAt(), snapshot.status(),
                    snapshot.message());
            if (snapshot.usable()) {
                succeeded++;
            } else if (snapshot.status() != CollectStatus.SKIPPED) {
                failed++;
            }
        }
        store.finishRun(runId, time.now(), accounts.size(), succeeded, failed, null);
        log.info("采集完成: runId={}, total={}, succeeded={}, failed={}",
                runId, accounts.size(), succeeded, failed);
        return new Summary(runId, accounts.size(), succeeded, failed);
    }

    public AccountSnapshot collectOne(UpstreamAccount account) {
        Instant now = time.now();
        if (!account.enabled()) {
            return AccountSnapshot.skipped(account.id(), now, "账号已停用");
        }
        UpstreamClient client = clients.stream()
                .filter(candidate -> candidate.type() == account.platformType())
                .findFirst()
                .orElse(null);
        if (client == null) {
            return AccountSnapshot.failed(account.id(), now,
                    "暂不支持采集该平台类型: " + account.platformType());
        }
        String credential;
        try {
            credential = cipher.decrypt(account.credentialCipher());
        } catch (BusinessException ex) {
            return AccountSnapshot.failed(account.id(), now, ex.getMessage());
        }
        try {
            UpstreamClient.SnapshotPayload payload = client.fetch(account, credential);
            return new AccountSnapshot(null, account.id(), now, payload.status(), payload.balance(),
                    payload.usedQuota(), payload.requestCount(), payload.raw(), payload.message());
        } catch (BusinessException ex) {
            return AccountSnapshot.failed(account.id(), now, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("采集异常: account={}, reason={}", account.login(), ex.getMessage());
            return AccountSnapshot.failed(account.id(), now, "采集异常: " + ex.getMessage());
        }
    }

    /** Result of one collection run. */
    public record Summary(long runId, int total, int succeeded, int failed) {
    }
}
