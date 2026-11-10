package com.relayops.agent.domain.prompt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the final prompt from ordered {@link PromptSection}s.
 *
 * <p>Rules:</p>
 * <ol>
 *   <li>Sections are sorted by {@link PromptLayer#order()}, then by insertion order.</li>
 *   <li>Blank sections are dropped.</li>
 *   <li>A protected section always wins: once added, later same-named sections are ignored, and a
 *       protected section replaces an earlier overridable one.</li>
 *   <li>{@link #render(int)} drops the lowest-priority layers first when the budget is exceeded.</li>
 * </ol>
 */
public final class PromptStack {

    private static final String SEPARATOR = "\n\n";

    private final List<PromptSection> sections;

    private PromptStack(List<PromptSection> sections) {
        this.sections = List.copyOf(sections);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<PromptSection> sections() {
        return sections;
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }

    /** Renders all sections that fit into {@code maxChars}; the hard cut keeps a trailing ellipsis. */
    public String render(int maxChars) {
        if (maxChars <= 0) {
            return "";
        }
        List<PromptSection> remaining = new ArrayList<>(sections);
        String rendered = join(remaining);
        // Drop from the tail (lowest priority) but never drop the highest-priority section.
        while (rendered.length() > maxChars && remaining.size() > 1) {
            remaining.remove(remaining.size() - 1);
            rendered = join(remaining);
        }
        if (rendered.length() <= maxChars) {
            return rendered;
        }
        if (maxChars == 1) {
            return "…";
        }
        return rendered.substring(0, maxChars - 1) + "…";
    }

    public String render() {
        return join(sections);
    }

    private static String join(List<PromptSection> values) {
        StringBuilder builder = new StringBuilder();
        for (PromptSection section : values) {
            if (!section.hasContent()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(SEPARATOR);
            }
            builder.append('[').append(section.name()).append(']').append('\n').append(section.content());
        }
        return builder.toString();
    }

    /** Incremental builder that enforces the protected-section rule while collecting. */
    public static final class Builder {

        private final Map<String, PromptSection> byName = new LinkedHashMap<>();

        public Builder add(PromptSection section) {
            if (section == null || !section.hasContent()) {
                return this;
            }
            PromptSection existing = byName.get(section.name());
            if (existing == null) {
                byName.put(section.name(), section);
                return this;
            }
            if (!existing.overridable()) {
                return this; // protected block stays untouched
            }
            if (!section.overridable()) {
                byName.put(section.name(), section);
                return this;
            }
            byName.put(section.name(), section);
            return this;
        }

        public Builder addAll(List<PromptSection> sections) {
            if (sections != null) {
                sections.forEach(this::add);
            }
            return this;
        }

        public Builder add(String name, PromptLayer layer, String content, boolean overridable) {
            return add(new PromptSection(name, layer, content, overridable));
        }

        public PromptStack build() {
            List<PromptSection> ordered = new ArrayList<>(byName.values());
            ordered.sort(Comparator.comparingInt((PromptSection section) -> section.layer().order()));
            return new PromptStack(ordered);
        }
    }
}
