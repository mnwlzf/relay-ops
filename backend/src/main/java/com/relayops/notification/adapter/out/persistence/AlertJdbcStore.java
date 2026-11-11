package com.relayops.notification.adapter.out.persistence;

import com.relayops.notification.application.alert.AlertStore;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** Cooldown state, delivery history and silence rules. */
@Repository
public class AlertJdbcStore implements AlertStore {

    private final NamedParameterJdbcTemplate jdbc;

    public AlertJdbcStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Instant> lastSent() {
        List<Map.Entry<String, Instant>> rows = jdbc.query(
                "select account_id, alert_type, last_sent_at from notification_alert_state",
                new MapSqlParameterSource(),
                (rs, rowNum) -> Map.entry(
                        rs.getLong("account_id") + ":" + rs.getString("alert_type"),
                        rs.getObject("last_sent_at", OffsetDateTime.class).toInstant()));
        Map<String, Instant> result = new LinkedHashMap<>();
        rows.forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    @Override
    public void record(long accountId, String alertType, Instant at, String value) {
        jdbc.update("""
                insert into notification_alert_state (account_id, alert_type, last_sent_at, last_value)
                values (:accountId, :alertType, :at, :value)
                on conflict (account_id, alert_type) do update
                   set last_sent_at = excluded.last_sent_at,
                       last_value = excluded.last_value
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("alertType", alertType)
                .addValue("at", offset(at))
                .addValue("value", truncate(value, 120)));
    }

    @Override
    public void saveHistory(long accountId, String accountName, String alertType, String message,
                            String channels, Instant at) {
        jdbc.update("""
                insert into notification_alert_history
                    (account_id, account_name, alert_type, message, channels, created_at)
                values (:accountId, :accountName, :alertType, :message, :channels, :at)
                """, new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("accountName", truncate(accountName, 120))
                .addValue("alertType", alertType)
                .addValue("message", truncate(message, 500))
                .addValue("channels", truncate(channels, 60))
                .addValue("at", offset(at)));
    }

    @Override
    public List<AlertHistoryRow> history(int limit) {
        return jdbc.query("""
                select * from notification_alert_history order by created_at desc limit :limit
                """, new MapSqlParameterSource("limit", Math.max(1, limit)), (rs, rowNum) ->
                new AlertHistoryRow(rs.getLong("id"), rs.getLong("account_id"),
                        rs.getString("account_name"), rs.getString("alert_type"),
                        rs.getString("message"), rs.getString("channels"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()));
    }

    @Override
    public List<SilenceRule> silences() {
        return jdbc.query("select * from notification_silence_rule order by until desc",
                new MapSqlParameterSource(), silenceMapper());
    }

    @Override
    public List<SilenceRule> activeSilences(Instant now) {
        return jdbc.query("select * from notification_silence_rule where until > :now",
                new MapSqlParameterSource("now", offset(now)), silenceMapper());
    }

    @Override
    public long addSilence(String scopeType, String scopeKey, String alertType, Instant until, String reason) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                insert into notification_silence_rule (scope_type, scope_key, alert_type, until, reason)
                values (:scopeType, :scopeKey, :alertType, :until, :reason)
                """, new MapSqlParameterSource()
                .addValue("scopeType", scopeType)
                .addValue("scopeKey", scopeKey)
                .addValue("alertType", alertType == null || alertType.isBlank() ? null : alertType)
                .addValue("until", offset(until))
                .addValue("reason", truncate(reason, 200)), keys, new String[]{"id"});
        Number key = keys.getKey();
        return key == null ? -1 : key.longValue();
    }

    @Override
    public void deleteSilence(long id) {
        jdbc.update("delete from notification_silence_rule where id = :id",
                new MapSqlParameterSource("id", id));
    }

    private static org.springframework.jdbc.core.RowMapper<SilenceRule> silenceMapper() {
        return (rs, rowNum) -> new SilenceRule(rs.getLong("id"), rs.getString("scope_type"),
                rs.getString("scope_key"), rs.getString("alert_type"),
                rs.getObject("until", OffsetDateTime.class).toInstant(), rs.getString("reason"));
    }

    private static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
