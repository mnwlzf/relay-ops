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
import org.springframework.stereotype.Repository;

/** Cooldown state for alerts. */
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
                .addValue("at", OffsetDateTime.ofInstant(at, ZoneId.systemDefault()))
                .addValue("value", value == null || value.length() <= 120 ? value : value.substring(0, 120)));
    }
}
