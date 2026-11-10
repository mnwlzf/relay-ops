package com.relayops.assistant.application.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relayops.agent.contract.EmbeddingClient;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.domain.memory.FactSource;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.time.BusinessTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T02:00:00Z");
    private static final ScopeRef GROUP = ScopeRef.group("qq:group:100");
    private static final BusinessTime TIME =
            new BusinessTime(Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));

    private static MemoryFact fact(long id, String value, double confidence, Instant createdAt) {
        return new MemoryFact(id, MemoryNamespace.GENERAL, GROUP, "preference", null, value,
                confidence, FactSource.EXPLICIT, Map.of(), null, null, createdAt);
    }

    private static MemoryFact draft(String value) {
        return new MemoryFact(null, MemoryNamespace.GENERAL, GROUP, "preference", "style", value,
                0.9, FactSource.EXPLICIT, Map.of(), null, null, NOW);
    }

    @Test
    void fallsBackToKeywordWhenEmbeddingIsUnavailable() {
        FakeStore store = new FakeStore();
        store.facts.add(fact(1, "号池缓存率偏低", 0.9, NOW.minusSeconds(60)));
        store.facts.add(fact(2, "喜欢简短回答", 0.8, NOW.minusSeconds(60)));
        MemoryService service = new MemoryService(store, new FakeEmbedding(false), TIME);

        MemoryService.Recalled recalled = service.recall(MemoryNamespace.GENERAL, GROUP, "缓存", 5);

        assertThat(recalled.mode()).isEqualTo("keyword");
        assertThat(recalled.facts()).extracting(MemoryFact::id).containsExactly(1L);
        assertThat(store.touched).containsExactly(1L);
    }

    @Test
    void hybridRecallAddsVectorNeighboursAndKeepsTheBestFirst() {
        FakeStore store = new FakeStore();
        store.facts.add(fact(1, "号池缓存率偏低", 0.9, NOW.minusSeconds(86400)));
        store.facts.add(fact(3, "上周讨论过命中率问题", 0.8, NOW.minusSeconds(60)));
        store.distances.put(1L, 0.2);
        store.distances.put(3L, 0.0);
        FakeEmbedding embedding = new FakeEmbedding(true);
        MemoryService service = new MemoryService(store, embedding, TIME);

        MemoryService.Recalled recalled = service.recall(MemoryNamespace.GENERAL, GROUP, "缓存", 5);

        assertThat(recalled.mode()).isEqualTo("hybrid");
        assertThat(recalled.facts()).extracting(MemoryFact::id).containsExactly(1L, 3L);
        assertThat(recalled.facts()).hasSize(2);
    }

    @Test
    void rememberRejectsSecretsThroughTheGuard() {
        FakeStore store = new FakeStore();
        MemoryService service = new MemoryService(store, new FakeEmbedding(false), TIME);

        assertThatThrownBy(() -> service.remember(draft("key 是 sk-abcdefgh12345678")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密钥");
        assertThat(store.facts).isEmpty();
    }

    @Test
    void rememberKeepsTheFactWhenEmbeddingFails() {
        FakeStore store = new FakeStore();
        FakeEmbedding embedding = new FakeEmbedding(true);
        embedding.failure = true;
        MemoryService service = new MemoryService(store, embedding, TIME);

        MemoryFact saved = service.remember(draft("偏好一句话回答"));

        assertThat(saved.id()).isNotNull();
        assertThat(store.facts).hasSize(1);
        assertThat(store.embeddingSaved).isFalse();
    }

    @Test
    void rememberStoresTheVectorWhenEmbeddingWorks() {
        FakeStore store = new FakeStore();
        MemoryService service = new MemoryService(store, new FakeEmbedding(true), TIME);

        MemoryFact saved = service.remember(draft("偏好一句话回答"));

        assertThat(saved.id()).isNotNull();
        assertThat(store.embeddingSaved).isTrue();
        assertThat(store.savedModel).isEqualTo("test-embed");
    }

    private static final class FakeEmbedding implements EmbeddingClient {

        private final boolean available;
        private boolean failure;
        private final float[] vector = {1f, 0f};

        private FakeEmbedding(boolean available) {
            this.available = available;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public int dimensions() {
            return 2;
        }

        @Override
        public String model() {
            return "test-embed";
        }

        @Override
        public Optional<float[]> embed(String text) {
            if (failure) {
                throw new IllegalStateException("远程向量化失败");
            }
            return available ? Optional.of(vector) : Optional.empty();
        }
    }

    private static final class FakeStore implements MemoryStore {

        private final List<MemoryFact> facts = new ArrayList<>();
        private final Map<Long, Double> distances = new HashMap<>();
        private final List<Long> touched = new ArrayList<>();
        private boolean embeddingSaved;
        private String savedModel;
        private long nextId = 100;

        @Override
        public long save(MemoryFact fact) {
            long id = nextId++;
            facts.add(new MemoryFact(id, fact.namespace(), fact.scope(), fact.category(), fact.factKey(),
                    fact.value(), fact.confidence(), fact.source(), fact.provenance(), fact.expiresAt(),
                    fact.lastUsedAt(), fact.createdAt()));
            return id;
        }

        @Override
        public Optional<MemoryFact> find(long id) {
            return facts.stream().filter(fact -> fact.id() == id).findFirst();
        }

        @Override
        public void delete(long id) {
            facts.removeIf(fact -> fact.id() == id);
        }

        @Override
        public List<MemoryFact> recent(MemoryNamespace namespace, ScopeRef scope, int limit) {
            return facts.stream()
                    .sorted(Comparator.comparing(MemoryFact::createdAt).reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<MemoryFact> search(MemoryNamespace namespace, ScopeRef scope, String query, int limit) {
            if (query == null || query.isBlank()) {
                return recent(namespace, scope, limit);
            }
            return facts.stream()
                    .filter(fact -> fact.value().contains(query))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<ScoredFact> similar(MemoryNamespace namespace, ScopeRef scope, String model, int dim,
                                        float[] vector, int limit) {
            return facts.stream()
                    .filter(fact -> distances.containsKey(fact.id()))
                    .map(fact -> new ScoredFact(fact, distances.get(fact.id())))
                    .sorted(Comparator.comparingDouble(ScoredFact::distance))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void saveEmbedding(long factId, String model, int dim, float[] vector) {
            embeddingSaved = true;
            savedModel = model;
        }

        @Override
        public void touch(List<Long> factIds, Instant now) {
            touched.addAll(factIds);
        }

        @Override
        public int purgeExpired() {
            return 0;
        }
    }
}
