package com.relayops.agent.contract;

import java.util.Optional;

/** Converts text to a vector; implementations degrade to {@link #available()} == false. */
public interface EmbeddingClient {

    /** Whether embedding calls are possible right now. */
    boolean available();

    /** Configured dimension; every stored vector must match it. */
    int dimensions();

    /** Model id recorded next to each stored vector. */
    String model();

    /** Empty when unavailable, when the call fails, or when the response dimension is wrong. */
    Optional<float[]> embed(String text);
}
