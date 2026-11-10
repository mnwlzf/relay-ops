package com.relayops.assistant.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ScopeType;
import com.relayops.assistant.application.memory.MemoryStore;
import com.relayops.assistant.domain.memory.FactSource;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Persistence of long-term facts and their vectors. */
@Repository
public class MemoryRepository implements MemoryStore {

    private final AssistantMemoryFactMapper mapper;
    private final ObjectMapper objectMapper;

    public MemoryRepository(AssistantMemoryFactMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public long save(MemoryFact fact) {
        AssistantMemoryFactEntity entity = new AssistantMemoryFactEntity();
        entity.setNamespace(fact.namespace().name());
        entity.setScopeType(fact.scope().type().name());
        entity.setScopeKey(fact.scope().key());
        entity.setCategory(fact.category());
        entity.setFactKey(fact.factKey());
        entity.setFactValue(fact.value());
        entity.setConfidence(BigDecimal.valueOf(fact.confidence()));
        entity.setSource(fact.source().name());
        entity.setProvenance(writeJson(fact.provenance()));
        entity.setExpiresAt(toOffset(fact.expiresAt()));
        entity.setCreatedAt(OffsetDateTime.now());
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public Optional<MemoryFact> find(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toFact);
    }

    @Override
    public void delete(long id) {
        mapper.deleteById(id);
    }

    @Override
    public List<MemoryFact> recent(MemoryNamespace namespace, ScopeRef scope, int limit) {
        return mapper.selectActive(namespace.name(), scope.type().name(), scope.key(), limit).stream()
                .map(this::toFact)
                .toList();
    }

    @Override
    public List<MemoryFact> search(MemoryNamespace namespace, ScopeRef scope, String query, int limit) {
        if (query == null || query.isBlank()) {
            return recent(namespace, scope, limit);
        }
        String pattern = "%" + escapeLike(query.strip()) + "%";
        return mapper.searchByKeyword(namespace.name(), scope.type().name(), scope.key(), pattern, limit)
                .stream()
                .map(this::toFact)
                .toList();
    }

    @Override
    public List<ScoredFact> similar(MemoryNamespace namespace, ScopeRef scope, String model, int dim,
                                    float[] vector, int limit) {
        List<MemoryMatchRow> rows = mapper.selectSimilar(namespace.name(), scope.type().name(), scope.key(),
                model, dim, toSqlVector(vector), limit);
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, MemoryFact> facts = new LinkedHashMap<>();
        List<Long> ids = rows.stream().map(MemoryMatchRow::getFactId).toList();
        mapper.selectBatchIds(ids).forEach(entity -> facts.put(entity.getId(), toFact(entity)));
        List<ScoredFact> scored = new ArrayList<>(rows.size());
        for (MemoryMatchRow row : rows) {
            MemoryFact fact = facts.get(row.getFactId());
            if (fact != null) {
                scored.add(new ScoredFact(fact, row.getDistance() == null ? 1.0 : row.getDistance()));
            }
        }
        return scored;
    }

    @Override
    public void saveEmbedding(long factId, String model, int dim, float[] vector) {
        mapper.upsertEmbedding(factId, model, dim, toSqlVector(vector));
    }

    @Override
    public void touch(List<Long> factIds, Instant now) {
        if (factIds.isEmpty()) {
            return;
        }
        mapper.touch(factIds, OffsetDateTime.ofInstant(now, ZoneId.systemDefault()));
    }

    @Override
    public int purgeExpired() {
        return mapper.deleteExpired();
    }

    private MemoryFact toFact(AssistantMemoryFactEntity entity) {
        return new MemoryFact(
                entity.getId(),
                MemoryNamespace.valueOf(entity.getNamespace()),
                new ScopeRef(ScopeType.valueOf(entity.getScopeType()), entity.getScopeKey()),
                entity.getCategory(),
                entity.getFactKey(),
                entity.getFactValue(),
                entity.getConfidence() == null ? 0.8 : entity.getConfidence().doubleValue(),
                FactSource.valueOf(entity.getSource()),
                readJson(entity.getProvenance()),
                toInstant(entity.getExpiresAt()),
                toInstant(entity.getLastUsedAt()),
                toInstant(entity.getCreatedAt()));
    }

    /** Renders a vector literal for {@code cast(? as vector)}. */
    static String toSqlVector(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 8).append('[');
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Map<String, String> readJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            return null;
        }
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
