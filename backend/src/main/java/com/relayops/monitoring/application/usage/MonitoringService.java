package com.relayops.monitoring.application.usage;

import com.relayops.monitoring.application.usage.UsageReadModel.AbnormalAccount;
import com.relayops.monitoring.application.usage.UsageReadModel.AccountUsage;
import com.relayops.monitoring.application.usage.UsageReadModel.Overview;
import com.relayops.monitoring.application.usage.UsageReadModel.SnapshotRow;
import com.relayops.monitoring.application.usage.UsageReadModel.TrendPoint;
import com.relayops.monitoring.domain.UsageMath;
import com.relayops.shared.time.BusinessTime;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Read side of the platform: current standing, today's consumption, abnormal accounts and trends.
 *
 * <p>Today's consumption is a snapshot delta, not an upstream field: New API has no "today" counter,
 * and computing it here keeps the collection layer free of business assumptions.</p>
 */
@Service
public class MonitoringService {

    private final UsageReadStore store;
    private final BusinessTime time;
    private final int staleHours;

    public MonitoringService(UsageReadStore store, BusinessTime time,
                             @Value("${relay-ops.monitoring.stale-hours:2}") int staleHours) {
        this.store = store;
        this.time = time;
        this.staleHours = Math.max(1, staleHours);
    }

    public Overview overview() {
        Instant dayStart = LocalDate.now(time.zone()).atStartOfDay(time.zone()).toInstant();
        Map<Long, BigDecimal> firstOfDay = new LinkedHashMap<>();
        for (SnapshotRow row : store.earliestSince(dayStart)) {
            firstOfDay.put(row.accountId(), row.usedQuota());
        }

        List<AccountUsage> accounts = store.latest().stream()
                .map(row -> new AccountUsage(
                        row.accountId(), row.accountName(), row.platformCode(), row.platformType(),
                        row.balance(), row.usedQuota(), row.requestCount(), row.status(),
                        row.collectedAt(),
                        UsageMath.increase(firstOfDay.get(row.accountId()), row.usedQuota()).orElse(null),
                        row.message()))
                .toList();
        return new Overview(accounts);
    }

    /** Accounts worth looking at: failed, partial, never collected, or silently stale. */
    public List<AbnormalAccount> abnormal() {
        Instant staleBefore = time.now().minus(Duration.ofHours(staleHours));
        return store.staleAccounts(staleBefore).stream()
                .map(row -> new AbnormalAccount(row.accountId(), row.accountName(), row.platformCode(),
                        reasonOf(row), row.lastMessage(), row.lastCollectedAt()))
                .toList();
    }

    public List<TrendPoint> trend(long accountId, int hours) {
        int window = Math.max(1, Math.min(hours, 24 * 30));
        Instant since = time.now().minus(Duration.ofHours(window));
        return store.trend(accountId, since).stream()
                .map(row -> new TrendPoint(row.collectedAt(), row.balance(), row.usedQuota(),
                        row.requestCount(), row.status()))
                .toList();
    }

    private String reasonOf(UsageReadStore.StaleAccount row) {
        String status = row.lastStatus();
        if (status == null) {
            return "NEVER_COLLECTED";
        }
        if ("FAILED".equals(status)) {
            return "FAILED";
        }
        if ("PARTIAL".equals(status)) {
            return "PARTIAL";
        }
        return "STALE";
    }

    /** Exposed for the page header: whether anything needs attention. */
    public Optional<String> attentionHint(List<AbnormalAccount> abnormal) {
        return abnormal.isEmpty() ? Optional.empty() : Optional.of(abnormal.size() + " 个账号需要关注");
    }
}
