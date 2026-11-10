package com.relayops.monitoring.adapter.out.persistence;

import com.relayops.monitoring.application.usage.UsageReadModel.SnapshotRow;
import com.relayops.monitoring.application.usage.UsageReadStore;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries over the collection tables.
 *
 * <p>Deliberately read-only and column-limited: monitoring owns dashboards, upstream owns writes.
 * The coupling is at SQL level until upstream publishes a query contract; moving behind
 * {@code UpstreamQueryPort} is a follow-up, not a behaviour change.</p>
 */
@Repository
public class UsageReadRepository implements UsageReadStore {

    private static final String ROW_COLUMNS = """
            a.id as account_id, a.name as account_name, p.code as platform_code, p.type as platform_type,
            s.balance, s.used_quota, s.request_count, s.status, s.collected_at, s.message
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public UsageReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SnapshotRow> latest() {
        return jdbc.query("""
                select %s
                  from upstream_account a
                  join upstream_platform p on p.id = a.platform_id
                  left join lateral (
                        select * from upstream_account_snapshot x
                         where x.account_id = a.id
                         order by x.collected_at desc
                         limit 1
                  ) s on true
                 where a.enabled = true
                 order by s.collected_at desc nulls last, a.id
                """.formatted(ROW_COLUMNS), new MapSqlParameterSource(), rowMapper());
    }

    @Override
    public List<SnapshotRow> earliestSince(Instant since) {
        return jdbc.query("""
                select %s
                  from upstream_account a
                  join upstream_platform p on p.id = a.platform_id
                  join lateral (
                        select * from upstream_account_snapshot x
                         where x.account_id = a.id and x.collected_at >= :since
                         order by x.collected_at asc
                         limit 1
                  ) s on true
                 where a.enabled = true
                """.formatted(ROW_COLUMNS),
                new MapSqlParameterSource("since", offset(since)), rowMapper());
    }

    @Override
    public List<SnapshotRow> trend(long accountId, Instant since) {
        return jdbc.query("""
                select %s
                  from upstream_account a
                  join upstream_platform p on p.id = a.platform_id
                  join upstream_account_snapshot s on s.account_id = a.id
                 where a.id = :accountId and s.collected_at >= :since
                 order by s.collected_at asc
                """.formatted(ROW_COLUMNS),
                new MapSqlParameterSource("accountId", accountId).addValue("since", offset(since)),
                rowMapper());
    }

    @Override
    public List<StaleAccount> staleAccounts(Instant staleBefore) {
        return jdbc.query("""
                select a.id, a.name, p.code as platform_code,
                       a.last_collect_status, a.last_collect_message, a.last_collect_at
                  from upstream_account a
                  join upstream_platform p on p.id = a.platform_id
                 where a.enabled = true
                   and (a.last_collect_status is null
                     or a.last_collect_status <> 'SUCCESS'
                     or a.last_collect_at is null
                     or a.last_collect_at < :staleBefore)
                 order by a.last_collect_at nulls first, a.id
                """, new MapSqlParameterSource("staleBefore", offset(staleBefore)),
                (rs, rowNum) -> new StaleAccount(
                        rs.getLong("id"), rs.getString("name"), rs.getString("platform_code"),
                        rs.getString("last_collect_status"), rs.getString("last_collect_message"),
                        toInstant(rs.getObject("last_collect_at", OffsetDateTime.class))));
    }

    private static RowMapper<SnapshotRow> rowMapper() {
        return (rs, rowNum) -> new SnapshotRow(
                rs.getLong("account_id"), rs.getString("account_name"),
                rs.getString("platform_code"), rs.getString("platform_type"),
                rs.getBigDecimal("balance"), rs.getBigDecimal("used_quota"),
                rs.getObject("request_count") == null ? null : rs.getLong("request_count"),
                rs.getString("status"),
                toInstant(rs.getObject("collected_at", OffsetDateTime.class)),
                rs.getString("message"));
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
