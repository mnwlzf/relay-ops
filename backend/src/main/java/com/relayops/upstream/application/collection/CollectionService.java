package com.relayops.upstream.application.collection;

import com.relayops.shared.time.BusinessTime;
import com.relayops.upstream.application.security.CredentialCipher;
import com.relayops.upstream.domain.AccountSnapshot;
import com.relayops.upstream.domain.CollectStatus;
import com.relayops.upstream.domain.UpstreamAccount;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Collection orchestration: one row per account per run, plus one run record.
 *
 * <p>Accounts are collected in parallel with a small fixed pool: a hundred accounts over a slow
 * upstream should not take a hundred sequential round-trips, but unbounded parallelism would hammer
 * the upstream and trip its rate limits. A failing account never aborts the run.</p>
 */
@Service
public class CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionService.class);

    private final UpstreamStore store;
    private final CredentialCipher cipher;
    private final List<UpstreamClient> clients;
    private final BusinessTime time;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final int concurrency;

    public CollectionService(UpstreamStore store, CredentialCipher cipher,
                             List<UpstreamClient> clients, BusinessTime time,
                             org.springframework.context.ApplicationEventPublisher events,
                             @Value("${relay-ops.upstream.collect-concurrency:4}") int concurrency) {
        this.store = store;
        this.cipher = cipher;
        this.clients = List.copyOf(clients);
        this.time = time;
        this.events = events;
        this.concurrency = Math.max(1, Math.min(concurrency, 16));
    }

    public Summary collectAll(String triggerSource) {
        Instant startedAt = time.now();
        long runId = store.startRun(triggerSource, startedAt);
        List<UpstreamAccount> accounts = store.enabledAccounts();
        if (accounts.isEmpty()) {
            store.finishRun(runId, time.now(), 0, 0, 0, "没有启用的账号");
            return new Summary(runId, 0, 0, 0);
        }

        List<AccountSnapshot> snapshots = collectInParallel(accounts);
        int succeeded = 0;
        int failed = 0;
        for (int index = 0; index < accounts.size(); index++) {
            UpstreamAccount account = accounts.get(index);
            AccountSnapshot snapshot = snapshots.get(index);
            store.insertSnapshot(snapshot);
            store.updateAccountStatus(account.id(), snapshot.collectedAt(), snapshot.status(),
                    snapshot.message());
            if (snapshot.usable()) {
                succeeded++;
                publishCollected(account, snapshot);
            } else if (snapshot.status() != CollectStatus.SKIPPED) {
                failed++;
            }
        }
        store.finishRun(runId, time.now(), accounts.size(), succeeded, failed, null);
        log.info("采集完成: runId={}, total={}, succeeded={}, failed={}, concurrency={}",
                runId, accounts.size(), succeeded, failed, concurrency);
        return new Summary(runId, accounts.size(), succeeded, failed);
    }

    /** Preserves input order so snapshots line up with accounts regardless of completion order. */
    private List<AccountSnapshot> collectInParallel(List<UpstreamAccount> accounts) {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrency, accounts.size()));
        try {
            List<Future<AccountSnapshot>> futures = new ArrayList<>(accounts.size());
            for (UpstreamAccount account : accounts) {
                futures.add(pool.submit(() -> collectOne(account)));
            }
            List<AccountSnapshot> results = new ArrayList<>(accounts.size());
            for (int index = 0; index < futures.size(); index++) {
                try {
                    results.add(futures.get(index).get(60, TimeUnit.SECONDS));
                } catch (ExecutionException ex) {
                    results.add(AccountSnapshot.failed(accounts.get(index).id(), time.now(),
                            "采集异常: " + rootMessage(ex)));
                } catch (java.util.concurrent.TimeoutException ex) {
                    results.add(AccountSnapshot.failed(accounts.get(index).id(), time.now(), "采集超时"));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    results.add(AccountSnapshot.failed(accounts.get(index).id(), time.now(), "采集被中断"));
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
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
        } catch (com.relayops.shared.error.BusinessException ex) {
            return AccountSnapshot.failed(account.id(), now, ex.getMessage());
        }
        try {
            UpstreamClient.SnapshotPayload payload = client.fetch(account, credential);
            return new AccountSnapshot(null, account.id(), now, payload.status(), payload.balance(),
                    payload.usedQuota(), payload.requestCount(), payload.raw(), payload.message());
        } catch (com.relayops.shared.error.BusinessException ex) {
            return AccountSnapshot.failed(account.id(), now, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("采集异常: account={}, reason={}", account.login(), ex.getMessage());
            return AccountSnapshot.failed(account.id(), now, "采集异常: " + ex.getMessage());
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }

    /** Consumers of AccountCollectedEvent (dashboards, alerts) must not break collection. */
    private void publishCollected(UpstreamAccount account, AccountSnapshot snapshot) {
        try {
            events.publishEvent(new com.relayops.upstream.contract.event.AccountCollectedEvent(
                    account.platformId(), account.id(), snapshot.collectedAt()));
        } catch (RuntimeException ex) {
            log.warn("发布采集事件失败: account={}, reason={}", account.id(), ex.getMessage());
        }
    }

    /** Result of one collection run. */
    public record Summary(long runId, int total, int succeeded, int failed) {
    }
}
