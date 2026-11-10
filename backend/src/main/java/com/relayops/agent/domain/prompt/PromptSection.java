package com.relayops.agent.domain.prompt;

import java.util.Objects;

/**
 * One named block of the prompt.
 *
 * <p>{@code overridable = false} marks a protected block: a persona (or a plugin persona patch) can
 * append to it but never replace it. That is what keeps "口径与脱敏" out of reach of roleplay.</p>
 */
public record PromptSection(String name, PromptLayer layer, String content, boolean overridable) {

    public PromptSection {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(layer, "layer");
        content = content == null ? "" : content.strip();
    }

    public static PromptSection protectedSection(String name, PromptLayer layer, String content) {
        return new PromptSection(name, layer, content, false);
    }

    public static PromptSection overridableSection(String name, PromptLayer layer, String content) {
        return new PromptSection(name, layer, content, true);
    }

    public boolean hasContent() {
        return !content.isEmpty();
    }
}
