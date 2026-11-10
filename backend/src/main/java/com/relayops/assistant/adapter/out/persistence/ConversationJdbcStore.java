package com.relayops.assistant.adapter.out.persistence;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ScopeType;
import com.relayops.assistant.application.memory.ConversationStore;
import com.relayops.assistant.domain.memory.Conversation;
import com.relayops.assistant.domain.memory.ConversationMessage;
import com.relayops.assistant.domain.memory.MemorySummary;
import com.relayops.assistant.domain.memory.MessageRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only persistence for L1/L2 memory.
 *
 * <p>Uses explicit SQL rather than an ORM entity: these tables are written far more often than they
 * are updated, and the interesting parts are a sequence allocation and a lateral join, both of which
 * read better as SQL.</p>
 */
@Repository
public class ConversationJdbcStore implements ConversationStore {

    private static final String ENSURE = """
            insert into assistant_conversation
                (scope_type, scope_key, pack_id, persona_id, persona_version, created_at, updated_at)
            values (:scopeType, :scopeKey, :packId, :personaId, :personaVersion, :now, :now)
            on conflict (scope_type, scope_key, pack_id) do update
               set updated_at = excluded.updated_at,
                   persona_id = excluded.persona_id,
                   persona_version = excluded.persona_version
            returning id
            """;

    private static final String LOCK_CONVERSATION =
            "select id from assistant_conversation where id = :id for update";

    private static final String APPEND = """
            insert into assistant_message
                (conversation_id, seq, role, speaker_id, speaker_name, content, redacted, created_at)
            values (:conversationId,
                    coalesce((select max(seq) from assistant_message where conversation_id = :conversationId), 0) + 1,
                    :role, :speakerId, :speakerName, :content, :redacted, :now)
            returning id, seq
            """;

    private static final String TOUCH_CONVERSATION =
            "update assistant_conversation set last_message_at = :now, updated_at = :now where id = :id";

    private static final String RECENT = """
            select * from (
                select * from assistant_message
                 where conversation_id = :conversationId
                 order by seq desc
                 limit :limit
            ) recent
            order by seq asc
            """;

    private static final String LATEST_SUMMARY = """
            select * from assistant_memory_summary
             where conversation_id = :conversationId
             order by created_at desc
             limit 1
            """;

    private static final String SAVE_SUMMARY = """
            insert into assistant_memory_summary
                (conversation_id, upto_message_id, summary, tokens, model, created_at)
            values (:conversationId, :uptoMessageId, :summary, :tokens, :model, :now)
            returning id
            """;

    private static final String NEEDING_SUMMARY = """
            select c.id
              from assistant_conversation c
              left join lateral (
                    select max(upto_message_id) as upto
                      from assistant_memory_summary s
                     where s.conversation_id = c.id
              ) s on true
              left join lateral (
                    select max(id) as newest, count(*) as total
                      from assistant_message m
                     where m.conversation_id = c.id
              ) m on true
             where c.status = 'ACTIVE'
               and coalesce(m.total, 0) >= :minMessages
               and coalesce(m.newest, 0) > coalesce(s.upto, 0)
             order by c.last_message_at nulls last
             limit :limit
            """;

    private static final String MESSAGE_COUNT =
            "select count(*) from assistant_message where conversation_id = :conversationId";

    private final NamedParameterJdbcTemplate jdbc;

    public ConversationJdbcStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public long ensure(ScopeRef scope, String packId, Long personaId, Integer personaVersion, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("scopeType", scope.type().name())
                .addValue("scopeKey", scope.key())
                .addValue("packId", packId)
                .addValue("personaId", personaId)
                .addValue("personaVersion", personaVersion)
                .addValue("now", toOffset(now));
        Long id = jdbc.queryForObject(ENSURE, params, Long.class);
        return id == null ? -1 : id;
    }

    @Override
    public Optional<Conversation> find(long conversationId) {
        List<Conversation> found = jdbc.query(
                "select * from assistant_conversation where id = :id",
                new MapSqlParameterSource("id", conversationId), conversationMapper());
        return found.stream().findFirst();
    }

    @Override
    @Transactional
    public long append(long conversationId, MessageRole role, String speakerId, String speakerName,
                       String content, boolean redacted, Instant now) {
        // Serialise sequence allocation per conversation instead of retrying unique violations.
        jdbc.queryForObject(LOCK_CONVERSATION, new MapSqlParameterSource("id", conversationId), Long.class);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("conversationId", conversationId)
                .addValue("role", role.name())
                .addValue("speakerId", speakerId)
                .addValue("speakerName", speakerName)
                .addValue("content", content)
                .addValue("redacted", redacted)
                .addValue("now", toOffset(now));
        Long id = jdbc.queryForObject(APPEND, params, (rs, rowNum) -> rs.getLong("id"));
        jdbc.update(TOUCH_CONVERSATION, new MapSqlParameterSource()
                .addValue("id", conversationId).addValue("now", toOffset(now)));
        return id == null ? -1 : id;
    }

    @Override
    public List<ConversationMessage> recent(long conversationId, int limit) {
        return jdbc.query(RECENT, new MapSqlParameterSource()
                .addValue("conversationId", conversationId)
                .addValue("limit", Math.max(1, limit)), messageMapper());
    }

    @Override
    public Optional<MemorySummary> latestSummary(long conversationId) {
        List<MemorySummary> found = jdbc.query(LATEST_SUMMARY,
                new MapSqlParameterSource("conversationId", conversationId), summaryMapper());
        return found.stream().findFirst();
    }

    @Override
    public void saveSummary(long conversationId, long uptoMessageId, String summary, Integer tokens,
                            String model, Instant now) {
        jdbc.update(SAVE_SUMMARY, new MapSqlParameterSource()
                .addValue("conversationId", conversationId)
                .addValue("uptoMessageId", uptoMessageId)
                .addValue("summary", summary)
                .addValue("tokens", tokens)
                .addValue("model", model)
                .addValue("now", toOffset(now)));
    }

    @Override
    public List<Long> needingSummary(int minMessages, int limit) {
        return jdbc.queryForList(NEEDING_SUMMARY, new MapSqlParameterSource()
                .addValue("minMessages", Math.max(1, minMessages))
                .addValue("limit", Math.max(1, limit)), Long.class);
    }

    @Override
    public long messageCount(long conversationId) {
        Long count = jdbc.queryForObject(MESSAGE_COUNT,
                new MapSqlParameterSource("conversationId", conversationId), Long.class);
        return count == null ? 0 : count;
    }

    private static RowMapper<Conversation> conversationMapper() {
        return (rs, rowNum) -> new Conversation(
                rs.getLong("id"),
                new ScopeRef(ScopeType.valueOf(rs.getString("scope_type")), rs.getString("scope_key")),
                rs.getString("pack_id"),
                nullableLong(rs, "persona_id"),
                nullableInt(rs, "persona_version"),
                toInstant(rs, "last_message_at"),
                rs.getString("status"),
                toInstant(rs, "created_at"));
    }

    private static RowMapper<ConversationMessage> messageMapper() {
        return (rs, rowNum) -> new ConversationMessage(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getLong("seq"),
                MessageRole.valueOf(rs.getString("role")),
                rs.getString("speaker_id"),
                rs.getString("speaker_name"),
                rs.getString("content"),
                rs.getBoolean("redacted"),
                toInstant(rs, "created_at"));
    }

    private static RowMapper<MemorySummary> summaryMapper() {
        return (rs, rowNum) -> new MemorySummary(
                rs.getLong("id"),
                rs.getLong("conversation_id"),
                rs.getLong("upto_message_id"),
                rs.getString("summary"),
                nullableInt(rs, "tokens"),
                rs.getString("model"),
                toInstant(rs, "created_at"));
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
