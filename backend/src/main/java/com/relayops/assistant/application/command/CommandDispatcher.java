package com.relayops.assistant.application.command;

import com.relayops.assistant.application.chat.ChatService;
import java.util.Optional;

/**
 * Deterministic commands, e.g. {@code /help} or {@code /report demo.daily-note}.
 *
 * <p>Commands never touch the model: they are the fallback when no model is configured, and the
 * stable interface for operators who would rather not phrase things as a question.</p>
 */
public interface CommandDispatcher {

    /** Returns the reply text, or empty when the message is not a command this router knows. */
    Optional<String> dispatch(ChatService.ChatRequest request);
}
