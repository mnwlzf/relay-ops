package com.relayops.monitoring.adapter.out.persistence;

import com.relayops.monitoring.application.usage.UsageReadModel.SnapshotRow;
import com.relayops.monitoring.application.usage.UsageReadStore;
import com.relayops.monitoring.contract.MonitoringQueryPort;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Implements the cross-module read contract on top of the monitoring read store. */
@Component
public class MonitoringQueryAdapter implements MonitoringQueryPort {

    private final UsageReadStore store;

    public MonitoringQueryAdapter(UsageReadStore store) {
        this.store = store;
    }

    @Override
    public Optional<UsageSummary> latestUsage(long accountId) {
        return store.latest().stream()
                .filter(row -> row.accountId() == accountId)
                .findFirst()
                .map(row -> new UsageSummary(row.accountId(),
                        row.balance() == null ? null : row.balance().toPlainString(),
                        row.usedQuota() == null ? null : row.usedQuota().toPlainString(),
                        row.collectedAt()));
    }

    @Override
    public List<AccountState> accounts() {
        return store.latest().stream().map(this::toState).toList();
    }

    private AccountState toState(SnapshotRow row) {
        return new AccountState(row.accountId(), row.accountName(), row.platformCode(),
                row.balance(), row.usedQuota(), row.requestCount(), row.status(), row.collectedAt());
    }
}
