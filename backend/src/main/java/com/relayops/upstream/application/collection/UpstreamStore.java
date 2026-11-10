package com.relayops.upstream.application.collection;

import com.relayops.upstream.domain.AccountSnapshot;
import com.relayops.upstream.domain.CollectStatus;
import com.relayops.upstream.domain.PlatformType;
import com.relayops.upstream.domain.UpstreamAccount;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Storage port of the collection slice, so orchestration can be tested without a database. */
public interface UpstreamStore {

    long insertPlatform(String code, String name, PlatformType type, String baseUrl, boolean enabled);

    List<PlatformRow> platforms();

    Optional<PlatformRow> findPlatform(String code);

    long insertAccount(long platformId, String name, String login, String credentialCipher,
                       String credentialHint, boolean enabled);

    Optional<UpstreamAccount> findAccount(long id);

    List<UpstreamAccount> enabledAccounts();

    void updateAccountStatus(long accountId, Instant at, CollectStatus status, String message);

    long insertSnapshot(AccountSnapshot snapshot);

    List<AccountSnapshot> recentSnapshots(long accountId, int limit);

    long startRun(String triggerSource, Instant startedAt);

    void finishRun(long runId, Instant finishedAt, int total, int succeeded, int failed, String message);

    List<RunRow> recentRuns(int limit);

    /** Accounts joined with their latest snapshot, ordered by balance desc (nulls last). */
    List<DashboardRow> dashboard(int limit);

    /** Platform row. */
    record PlatformRow(Long id, String code, String name, PlatformType type, String baseUrl, boolean enabled) {
    }

    /** Collection run row. */
    record RunRow(Long id, String triggerSource, Instant startedAt, Instant finishedAt,
                  int total, int succeeded, int failed, String message) {
    }

    /** One dashboard row: account plus its most recent snapshot. */
    record DashboardRow(Long accountId, String accountName, String platformCode, PlatformType platformType,
                        BigDecimal balance, BigDecimal usedQuota, Long requestCount,
                        CollectStatus status, Instant collectedAt, String message) {
    }
}
