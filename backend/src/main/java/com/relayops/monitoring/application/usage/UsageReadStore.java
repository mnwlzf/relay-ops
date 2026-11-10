package com.relayops.monitoring.application.usage;

import com.relayops.monitoring.application.usage.UsageReadModel.SnapshotRow;
import java.time.Instant;
import java.util.List;

/** Read-only port over collection snapshots. */
public interface UsageReadStore {

    /** Latest snapshot of every enabled account, newest collection first. */
    List<SnapshotRow> latest();

    /** Earliest snapshot per account since {@code since}; used for the daily delta. */
    List<SnapshotRow> earliestSince(Instant since);

    /** Snapshots of one account since {@code since}, ascending by time. */
    List<SnapshotRow> trend(long accountId, Instant since);

    /** Accounts whose last collection failed, is partial, or is older than {@code staleBefore}. */
    List<StaleAccount> staleAccounts(Instant staleBefore);

    /** Account plus why it looks stale. */
    record StaleAccount(long accountId, String accountName, String platformCode,
                        String lastStatus, String lastMessage, Instant lastCollectedAt) {
    }
}
