package com.relayops.upstream.adapter.out.persistence;

import com.relayops.upstream.application.collection.UpstreamStore;
import com.relayops.upstream.domain.AccountSnapshot;
import com.relayops.upstream.domain.CollectStatus;
import com.relayops.upstream.domain.PlatformType;
import com.relayops.upstream.domain.UpstreamAccount;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Collection persistence with explicit SQL.
 *
 * <p>Snapshots and runs are append-only and queried by time, so SQL is clearer than an ORM mapping;
 * credentials are stored as ciphertext and never selected into API models.</p>
 */
@Repository
public class UpstreamRepository implements UpstreamStore {

    private static final String ACCOUNT_COLUMNS = """
            a.id, a.platform_id, a.name, a.login, a.credential_cipher, a.credential_hint, a.enabled,
            a.last_collect_at, a.last_collect_status, a.last_collect_message,
            p.code as platform_code, p.type as platform_type, p.base_url
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public UpstreamRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long insertPlatform(String code, String name, PlatformType type, String baseUrl, boolean enabled) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("code", code).addValue("name", name).addValue("type", type.name())
                .addValue("baseUrl", baseUrl).addValue("enabled", enabled)
                .addValue("now", OffsetDateTime.now());
        return insert("""
                insert into upstream_platform (code, name, type, base_url, enabled, created_at, updated_at)
                values (:code, :name, :type, :baseUrl, :enabled, :now, :now)
                """, params);
    }

    @Override
    public List<PlatformRow> platforms() {
        return jdbc.query("select * from upstream_platform order by id", new MapSqlParameterSource(),
                (rs, rowNum) -> new PlatformRow(rs.getLong("id"), rs.getString("code"),
                        rs.getString("name"), PlatformType.valueOf(rs.getString("type")),
                        rs.getString("base_url"), rs.getBoolean("enabled")));
    }

    @Override
    public Optional<PlatformRow> findPlatform(String code) {
        return jdbc.query("select * from upstream_platform where code = :code",
                        new MapSqlParameterSource("code", code),
                        (rs, rowNum) -> new PlatformRow(rs.getLong("id"), rs.getString("code"),
                                rs.getString("name"), PlatformType.valueOf(rs.getString("type")),
                                rs.getString("base_url"), rs.getBoolean("enabled")))
                .stream().findFirst();
    }

    @Override
    public long insertAccount(long platformId, String name, String login, String credentialCipher,
                              String credentialHint, boolean enabled) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("platformId", platformId).addValue("name", name).addValue("login", login)
                .addValue("cipher", credentialCipher).addValue("hint", credentialHint)
                .addValue("enabled", enabled).addValue("now", OffsetDateTime.now());
        return insert("""
                insert into upstream_account
                    (platform_id, name, login, credential_cipher, credential_hint, enabled, created_at, updated_at)
                values (:platformId, :name, :login, :cipher, :hint, :enabled, :now, :now)
                """, params);
    }

    @Override
    public Optional<UpstreamAccount> findAccount(long id) {
        return jdbc.query("select " + ACCOUNT_COLUMNS + """
                          from upstream_account a join upstream_platform p on p.id = a.platform_id
                         where a.id = :id
                        """, new MapSqlParameterSource("id", id), accountMapper())
                .stream().findFirst();
    }

    @Override
    public List<UpstreamAccount> enabledAccounts() {
        return jdbc.query("select " + ACCOUNT_COLUMNS + """
                          from upstream_account a join upstream_platform p on p.id = a.platform_id
                         where a.enabled = true and p.enabled = true
                         order by a.id
                        """, new MapSqlParameterSource(), accountMapper());
    }

    @Override
    public void updateAccountStatus(long accountId, Instant at, CollectStatus status, String message) {
        jdbc.update("""
                update upstream_account
                   set last_collect_at = :at, last_collect_status = :status,
                       last_collect_message = :message, updated_at = :at
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("at", OffsetDateTime.ofInstant(at, ZoneId.systemDefault()))
                .addValue("status", status.name())
                .addValue("message", truncate(message, 500))
                .addValue("id", accountId));
    }

    @Override
    public long insertSnapshot(AccountSnapshot snapshot) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accountId", snapshot.accountId())
                .addValue("collectedAt", OffsetDateTime.ofInstant(snapshot.collectedAt(), ZoneId.systemDefault()))
                .addValue("status", snapshot.status().name())
                .addValue("balance", snapshot.balance())
                .addValue("usedQuota", snapshot.usedQuota())
                .addValue("requestCount", snapshot.requestCount())
                .addValue("raw", snapshot.raw())
                .addValue("message", truncate(snapshot.message(), 500));
        if (snapshot.raw() == null) {
            return insert("""
                    insert into upstream_account_snapshot
                        (account_id, collected_at, status, balance, used_quota, request_count, raw, message)
                    values (:accountId, :collectedAt, :status, :balance, :usedQuota, :requestCount, null, :message)
                    """, params);
        }
        return insert("""
                insert into upstream_account_snapshot
                    (account_id, collected_at, status, balance, used_quota, request_count, raw, message)
                values (:accountId, :collectedAt, :status, :balance, :usedQuota, :requestCount,
                        cast(:raw as jsonb), :message)
                """, params);
    }

    @Override
    public List<AccountSnapshot> recentSnapshots(long accountId, int limit) {
        return jdbc.query("""
                select * from upstream_account_snapshot
                 where account_id = :accountId
                 order by collected_at desc
                 limit :limit
                """, new MapSqlParameterSource("accountId", accountId).addValue("limit", Math.max(1, limit)),
                snapshotMapper());
    }

    @Override
    public long startRun(String triggerSource, Instant startedAt) {
        return insert("""
                insert into upstream_collect_run (trigger_source, started_at)
                values (:source, :startedAt)
                """, new MapSqlParameterSource("source", triggerSource)
                .addValue("startedAt", OffsetDateTime.ofInstant(startedAt, ZoneId.systemDefault())));
    }

    @Override
    public void finishRun(long runId, Instant finishedAt, int total, int succeeded, int failed, String message) {
        jdbc.update("""
                update upstream_collect_run
                   set finished_at = :at, total_accounts = :total, succeeded = :succeeded,
                       failed = :failed, message = :message
                 where id = :id
                """, new MapSqlParameterSource()
                .addValue("at", OffsetDateTime.ofInstant(finishedAt, ZoneId.systemDefault()))
                .addValue("total", total).addValue("succeeded", succeeded).addValue("failed", failed)
                .addValue("message", truncate(message, 1000)).addValue("id", runId));
    }

    @Override
    public List<RunRow> recentRuns(int limit) {
        return jdbc.query("""
                select * from upstream_collect_run order by started_at desc limit :limit
                """, new MapSqlParameterSource("limit", Math.max(1, limit)), (rs, rowNum) -> new RunRow(
                rs.getLong("id"), rs.getString("trigger_source"),
                toInstant(rs.getObject("started_at", OffsetDateTime.class)),
                toInstant(rs.getObject("finished_at", OffsetDateTime.class)),
                rs.getInt("total_accounts"), rs.getInt("succeeded"), rs.getInt("failed"),
                rs.getString("message")));
    }

    private long insert(String sql, MapSqlParameterSource params) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(sql, params, keys, new String[]{"id"});
        Number key = keys.getKey();
        return key == null ? -1 : key.longValue();
    }

    private static RowMapper<UpstreamAccount> accountMapper() {
        return (rs, rowNum) -> new UpstreamAccount(
                rs.getLong("id"), rs.getLong("platform_id"), rs.getString("platform_code"),
                PlatformType.valueOf(rs.getString("platform_type")), rs.getString("base_url"),
                rs.getString("name"), rs.getString("login"), rs.getString("credential_cipher"),
                rs.getString("credential_hint"), rs.getBoolean("enabled"),
                toInstant(rs.getObject("last_collect_at", OffsetDateTime.class)),
                statusOrNull(rs.getString("last_collect_status")),
                rs.getString("last_collect_message"));
    }

    private static RowMapper<AccountSnapshot> snapshotMapper() {
        return (rs, rowNum) -> new AccountSnapshot(
                rs.getLong("id"), rs.getLong("account_id"),
                toInstant(rs.getObject("collected_at", OffsetDateTime.class)),
                CollectStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("balance"), rs.getBigDecimal("used_quota"),
                rs.getObject("request_count") == null ? null : rs.getLong("request_count"),
                rs.getString("raw"), rs.getString("message"));
    }

    private static CollectStatus statusOrNull(String value) {
        return value == null ? null : CollectStatus.valueOf(value);
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
