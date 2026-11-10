package com.relayops.assistant.application.chat;

import java.util.Optional;

/**
 * One-shot completion: the assembled system prompt plus the user's message.
 *
 * <p>Empty means "no model": callers fall back to command-only mode instead of failing.</p>
 */
public interface ChatCompletion {

    boolean available();

    Optional<String> complete(String systemPrompt, String userMessage);
}
