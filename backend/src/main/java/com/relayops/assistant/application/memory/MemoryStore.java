package com.relayops.assistant.application.memory;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Storage port of long-term memory.
 *
 * <p>Kept as an interface so recall scoring and write policy can be tested without a database.</p>
 */
public interface MemoryStore {

    long save(MemoryFact fact);

    Optional<MemoryFact> find(long id);

    void delete(long id);

    List<MemoryFact> recent(MemoryNamespace namespace, ScopeRef scope, int limit);

    /** Substring match on value and key; the keyword half of hybrid recall. */
    List<MemoryFact> search(MemoryNamespace namespace, ScopeRef scope, String query, int limit);

    /** Cosine-distance neighbours; empty when no vector is stored for the given model. */
    List<ScoredFact> similar(MemoryNamespace namespace, ScopeRef scope, String model, int dim,
                             float[] vector, int limit);

    void saveEmbedding(long factId, String model, int dim, float[] vector);

    void touch(List<Long> factIds, Instant now);

    int purgeExpired();

    /** A neighbour with its cosine distance (0 = identical). */
    record ScoredFact(MemoryFact fact, double distance) {
    }
}
