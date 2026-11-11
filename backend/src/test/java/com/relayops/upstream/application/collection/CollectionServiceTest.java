package com.relayops.upstream.application.collection;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.shared.error.BusinessException;
import com.relayops.shared.time.BusinessTime;
import com.relayops.upstream.application.security.CredentialCipher;
import com.relayops.upstream.domain.AccountSnapshot;
import com.relayops.upstream.domain.CollectStatus;
import com.relayops.upstream.domain.PlatformType;
import com.relayops.upstream.domain.UpstreamAccount;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CollectionServiceTest {

    private static final String KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final BusinessTime TIME =
            new BusinessTime(Clock.fixed(Instant.parse("2026-11-10T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
    private static final CredentialCipher CIPHER = new CredentialCipher(KEY);

    private static UpstreamAccount account(long id, PlatformType type, boolean enabled) {
        return new UpstreamAccount(id, 1L, "demo", type, "https://api.example.com", "账号" + id,
                "login" + id, CIPHER.encrypt("token-" + id), "to****n", enabled, null, null, null);
    }

    private static CollectionService service(FakeStore store, UpstreamClient... clients) {
        return new CollectionService(store, CIPHER, List.of(clients), TIME, event -> { }, 2);
    }

    private static UpstreamClient okClient() {
        return new UpstreamClient() {
            @Override
            public PlatformType type() {
                return PlatformType.NEWAPI;
            }

            @Override
            public SnapshotPayload fetch(UpstreamAccount account, String credential) {
                return SnapshotPayload.success(new BigDecimal("10.5"), new BigDecimal("99"),
                        42L, "{\"ok\":true}");
            }
        };
    }

    @Test
    void successfulCollectionStoresSnapshotAndStatus() {
        FakeStore store = new FakeStore();
        store.accounts.add(account(1, PlatformType.NEWAPI, true));

        CollectionService.Summary summary = service(store, okClient()).collectAll("MANUAL");

        assertThat(summary.total()).isEqualTo(1);
        assertThat(summary.succeeded()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
        assertThat(store.snapshots).hasSize(1);
        assertThat(store.snapshots.get(0).status()).isEqualTo(CollectStatus.SUCCESS);
        assertThat(store.statuses).containsEntry(1L, CollectStatus.SUCCESS);
        assertThat(store.finished).isTrue();
    }

    @Test
    void unsupportedPlatformIsRecordedAsFailureWithoutStoppingTheRun() {
        FakeStore store = new FakeStore();
        store.accounts.add(account(1, PlatformType.NEWAPI, true));
        store.accounts.add(account(2, PlatformType.SUB2API, true));

        CollectionService.Summary summary = service(store, okClient()).collectAll("SCHEDULE");

        assertThat(summary.succeeded()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(store.snapshots).hasSize(2);
        assertThat(store.snapshots.get(1).message()).contains("暂不支持");
    }

    @Test
    void clientFailureIsCapturedOnTheSnapshot() {
        FakeStore store = new FakeStore();
        store.accounts.add(account(1, PlatformType.NEWAPI, true));
        UpstreamClient failing = new UpstreamClient() {
            @Override
            public PlatformType type() {
                return PlatformType.NEWAPI;
            }

            @Override
            public SnapshotPayload fetch(UpstreamAccount account, String credential) {
                throw new BusinessException("COLLECT_FAILED", "上游 401");
            }
        };

        CollectionService.Summary summary = service(store, failing).collectAll("MANUAL");

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(store.snapshots.get(0).status()).isEqualTo(CollectStatus.FAILED);
        assertThat(store.snapshots.get(0).message()).contains("401");
        assertThat(store.statuses.get(1L)).isEqualTo(CollectStatus.FAILED);
    }

    @Test
    void disabledAccountIsSkippedAndNotCountedAsFailure() {
        FakeStore store = new FakeStore();
        store.accounts.add(account(1, PlatformType.NEWAPI, false));

        CollectionService.Summary summary = service(store, okClient()).collectAll("MANUAL");

        assertThat(summary.failed()).isZero();
        assertThat(store.snapshots.get(0).status()).isEqualTo(CollectStatus.SKIPPED);
    }

    @Test
    void missingCredentialKeyFailsLoudlyPerAccount() {
        FakeStore store = new FakeStore();
        store.accounts.add(account(1, PlatformType.NEWAPI, true));
        CollectionService service = new CollectionService(store, new CredentialCipher(""),
                List.of(okClient()), TIME, event -> { }, 2);

        CollectionService.Summary summary = service.collectAll("MANUAL");

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(store.snapshots.get(0).message()).contains("RELAY_OPS_CREDENTIAL_KEY");
    }

    @Test
    void publishesCollectedEventForUsableSnapshots() {
        FakeStore store = new FakeStore();
        store.accounts.add(account(1, PlatformType.NEWAPI, true));
        List<Object> published = new ArrayList<>();
        CollectionService service = new CollectionService(store, CIPHER, List.of(okClient()), TIME,
                published::add, 2);

        service.collectAll("MANUAL");

        assertThat(published).hasSize(1);
        assertThat(published.get(0)).isInstanceOf(
                com.relayops.upstream.contract.event.AccountCollectedEvent.class);
    }

    private static final class FakeStore implements UpstreamStore {

        private final List<UpstreamAccount> accounts = new ArrayList<>();
        private final List<AccountSnapshot> snapshots = new ArrayList<>();
        private final java.util.Map<Long, CollectStatus> statuses = new java.util.HashMap<>();
        private boolean finished;

        @Override
        public long insertPlatform(String code, String name, PlatformType type, String baseUrl, boolean enabled) {
            return 1L;
        }

        @Override
        public List<PlatformRow> platforms() {
            return List.of();
        }

        @Override
        public Optional<PlatformRow> findPlatform(String code) {
            return Optional.of(new PlatformRow(1L, code, "演示", PlatformType.NEWAPI,
                    "https://api.example.com", true));
        }

        @Override
        public long insertAccount(long platformId, String name, String login, String credentialCipher,
                                  String credentialHint, boolean enabled) {
            return 1L;
        }

        @Override
        public Optional<UpstreamAccount> findAccount(long id) {
            return accounts.stream().filter(account -> account.id() == id).findFirst();
        }

        @Override
        public List<UpstreamAccount> enabledAccounts() {
            return accounts;
        }

        @Override
        public void updateAccountStatus(long accountId, Instant at, CollectStatus status, String message) {
            statuses.put(accountId, status);
        }

        @Override
        public long insertSnapshot(AccountSnapshot snapshot) {
            snapshots.add(snapshot);
            return snapshots.size();
        }

        @Override
        public List<AccountSnapshot> recentSnapshots(long accountId, int limit) {
            return snapshots;
        }

        @Override
        public long startRun(String triggerSource, Instant startedAt) {
            return 7L;
        }

        @Override
        public void finishRun(long runId, Instant finishedAt, int total, int succeeded, int failed, String message) {
            finished = true;
        }

        @Override
        public List<RunRow> recentRuns(int limit) {
            return List.of();
        }

        @Override
        public List<DashboardRow> dashboard(int limit) {
            return List.of();
        }
    }
}
