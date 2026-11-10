package com.relayops.assistant.application.memory;

import com.relayops.agent.contract.EmbeddingClient;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import com.relayops.shared.time.BusinessTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Long-term memory: guarded writes and hybrid recall.
 *
 * <p>Recall combines substring matches with vector neighbours when embeddings are available. Every
 * candidate is scored as {@code 0.45*similarity + 0.25*keyword + 0.20*confidence + 0.10*recency},
 * which keeps an exact keyword hit competitive with a semantic neighbour and keeps stale facts from
 * dominating. When embedding is unavailable the same weights degrade to keyword-only ranking.</p>
 */
@Service
public class MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private static final double SIMILARITY_WEIGHT = 0.45;
    private static final double KEYWORD_WEIGHT = 0.25;
    private static final double CONFIDENCE_WEIGHT = 0.20;
    private static final double RECENCY_WEIGHT = 0.10;
    private static final int RECENCY_HALF_LIFE_DAYS = 30;

    private final MemoryStore store;
    private final EmbeddingClient embedding;
    private final BusinessTime time;

    public MemoryService(MemoryStore store, EmbeddingClient embedding, BusinessTime time) {
        this.store = store;
        this.embedding = embedding;
        this.time = time;
    }

    /** Validates, stores, and (best effort) embeds a fact. Returns the stored fact with its id. */
    public MemoryFact remember(MemoryFact draft) {
        MemoryGuard.validate(draft);
        long id = store.save(draft);
        store.find(id).ifPresent(saved -> embedQuietly(saved));
        return store.find(id).orElse(draft);
    }

    /** Hybrid recall, already scoped and namespace-filtered; marks the hits as used. */
    public Recalled recall(MemoryNamespace namespace, ScopeRef scope, String query, int limit) {
        int wanted = Math.max(1, Math.min(limit, 20));
        int fetch = Math.min(wanted * 3, 60);
        Map<Long, Candidate> candidates = new LinkedHashMap<>();

        for (MemoryFact fact : store.search(namespace, scope, query, fetch)) {
            candidates.computeIfAbsent(fact.id(), id -> new Candidate(fact)).keywordHit = true;
        }

        boolean hybrid = false;
        if (embedding.available() && query != null && !query.isBlank()) {
            Optional<float[]> vector = embedding.embed(query);
            if (vector.isPresent()) {
                List<MemoryStore.ScoredFact> neighbours = store.similar(
                        namespace, scope, embedding.model(), embedding.dimensions(), vector.get(), fetch);
                for (MemoryStore.ScoredFact neighbour : neighbours) {
                    Candidate candidate = candidates.computeIfAbsent(
                            neighbour.fact().id(), id -> new Candidate(neighbour.fact()));
                    candidate.similarity = Math.max(candidate.similarity,
                            Math.max(0.0, Math.min(1.0, 1.0 - neighbour.distance())));
                    hybrid = true;
                }
            }
        }

        Instant now = time.now();
        List<Candidate> scored = new ArrayList<>(candidates.values());
        scored.forEach(candidate -> candidate.score = score(candidate, now));
        scored.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.score).reversed());

        List<MemoryFact> facts = scored.stream().limit(wanted).map(candidate -> candidate.fact).toList();
        store.touch(facts.stream().map(MemoryFact::id).toList(), now);
        return new Recalled(facts, hybrid ? "hybrid" : "keyword", facts.size());
    }

    public List<MemoryFact> recent(MemoryNamespace namespace, ScopeRef scope, int limit) {
        return store.recent(namespace, scope, Math.max(1, Math.min(limit, 100)));
    }

    public void forget(long id) {
        store.delete(id);
    }

    public int purgeExpired() {
        return store.purgeExpired();
    }

    private void embedQuietly(MemoryFact fact) {
        if (!embedding.available()) {
            return;
        }
        try {
            embedding.embed(fact.asPromptLine())
                    .ifPresent(vector -> store.saveEmbedding(fact.id(), embedding.model(),
                            embedding.dimensions(), vector));
        } catch (RuntimeException ex) {
            log.warn("写入向量失败，事实已保存但只能被关键词召回: factId={}", fact.id(), ex);
        }
    }

    private double score(Candidate candidate, Instant now) {
        double similarity = candidate.similarity;
        double keyword = candidate.keywordHit ? 1.0 : 0.0;
        double confidence = Math.max(0.0, Math.min(1.0, candidate.fact.confidence()));
        double recency = recency(candidate.fact.createdAt(), now);
        return SIMILARITY_WEIGHT * similarity
                + KEYWORD_WEIGHT * keyword
                + CONFIDENCE_WEIGHT * confidence
                + RECENCY_WEIGHT * recency;
    }

    /** Exponential decay with a 30-day half-life; unknown timestamps score 0.5. */
    private double recency(Instant createdAt, Instant now) {
        if (createdAt == null) {
            return 0.5;
        }
        long days = Math.max(0, Duration.between(createdAt, now).toDays());
        if (days == 0) {
            return 1.0;
        }
        return Math.exp(-Math.log(2) * days / RECENCY_HALF_LIFE_DAYS);
    }

    private static final class Candidate {
        private final MemoryFact fact;
        private boolean keywordHit;
        private double similarity;
        private double score;

        private Candidate(MemoryFact fact) {
            this.fact = fact;
        }
    }

    /**
     * Recall result.
     *
     * @param mode {@code hybrid} when vectors contributed, {@code keyword} when they could not
     */
    public record Recalled(List<MemoryFact> facts, String mode, int count) {
    }
}
