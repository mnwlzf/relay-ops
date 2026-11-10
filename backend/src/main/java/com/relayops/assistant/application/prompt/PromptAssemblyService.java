package com.relayops.assistant.application.prompt;

import com.relayops.agent.application.tool.ToolRegistry;
import com.relayops.agent.contract.CapabilityPack;
import com.relayops.agent.contract.PromptContext;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.domain.persona.PersonaCard;
import com.relayops.agent.domain.prompt.DefaultSections;
import com.relayops.agent.domain.prompt.PromptLayer;
import com.relayops.agent.domain.prompt.PromptSection;
import com.relayops.agent.domain.prompt.PromptStack;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.shared.error.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Assembles the final prompt for one request: kernel safety rules, the capability pack's rules, and
 * the persona's expression layer.
 *
 * <p>Used both by the chat pipeline and by the admin "preview prompt" button, so what an operator sees
 * is exactly what the model receives (modulo memory and session blocks, which are request-time).</p>
 */
@Component
public class PromptAssemblyService {

    public static final int DEFAULT_BUDGET = 6000;

    private final ToolRegistry toolRegistry;

    public PromptAssemblyService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public PromptStack assemble(String packId, PersonaCard persona, ScopeRef scope,
                                boolean admin, boolean platformUser) {
        return assemble(packId, persona, scope, admin, platformUser, MemoryPrompt.empty());
    }

    public PromptStack assemble(String packId, PersonaCard persona, ScopeRef scope,
                                boolean admin, boolean platformUser, MemoryPrompt memory) {
        CapabilityPack pack = toolRegistry.packs().stream()
                .filter(candidate -> candidate.id().equals(packId))
                .findFirst()
                .orElseThrow(() -> new BusinessException("PACK_NOT_FOUND", "能力包不存在: " + packId));

        PromptContext context = new PromptContext(scope, persona, admin, platformUser);
        List<PromptSection> sections = new ArrayList<>();
        sections.add(DefaultSections.safety());
        sections.addAll(pack.prompt(context));
        if (persona != null && persona.appliesTo(packId)) {
            sections.addAll(persona.sections());
        }
        appendMemory(sections, memory);
        return PromptStack.builder().addAll(sections).build();
    }

    /** L2 summary first, then the recalled facts, both inside the lowest-but-one layer. */
    private void appendMemory(List<PromptSection> sections, MemoryPrompt memory) {
        if (memory == null) {
            return;
        }
        if (memory.summary() != null && !memory.summary().isBlank()) {
            sections.add(PromptSection.overridableSection("会话摘要", PromptLayer.MEMORY, memory.summary()));
        }
        if (memory.facts() != null && !memory.facts().isEmpty()) {
            String text = memory.facts().stream()
                    .limit(10)
                    .map(fact -> "- " + fact.asPromptLine())
                    .collect(java.util.stream.Collectors.joining("\n"));
            sections.add(PromptSection.overridableSection("长期记忆", PromptLayer.MEMORY, text));
        }
    }

    public PromptPreview preview(String packId, PersonaCard persona, ScopeRef scope,
                                 boolean admin, boolean platformUser, Integer budget) {
        int limit = budget == null || budget <= 0 ? DEFAULT_BUDGET : Math.min(budget, 20000);
        PromptStack stack = assemble(packId, persona, scope, admin, platformUser);
        List<PromptSectionView> views = stack.sections().stream()
                .map(section -> new PromptSectionView(section.name(), section.layer().name(),
                        section.overridable(), section.content().length()))
                .toList();
        return new PromptPreview(stack.render(limit), limit, views);
    }

    /** Rendered prompt plus a structural summary, so the admin page can show both. */
    public record PromptPreview(String rendered, int budget, List<PromptSectionView> sections) {
    }

    /** One assembled section as shown in the preview. */
    public record PromptSectionView(String name, String layer, boolean overridable, int length) {
    }

    /**
     * Recalled memory handed to the prompt.
     *
     * @param summary rolling L2 summary, may be blank
     * @param facts   L3/L4 facts already namespace-filtered by the caller
     */
    public record MemoryPrompt(String summary, List<MemoryFact> facts) {

        public MemoryPrompt {
            facts = facts == null ? List.of() : List.copyOf(facts);
        }

        public static MemoryPrompt empty() {
            return new MemoryPrompt(null, List.of());
        }
    }
}
