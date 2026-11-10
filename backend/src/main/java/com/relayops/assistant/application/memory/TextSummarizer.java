package com.relayops.assistant.application.memory;

import java.util.Optional;

/** Turns a prompt into a short summary; empty when no model is available. */
public interface TextSummarizer {

    boolean available();

    Optional<String> summarize(String prompt);
}
