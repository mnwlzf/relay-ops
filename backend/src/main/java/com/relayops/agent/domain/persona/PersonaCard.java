package com.relayops.agent.domain.persona;

import com.relayops.agent.domain.prompt.PromptLayer;
import com.relayops.agent.domain.prompt.PromptSection;
import com.relayops.shared.error.BusinessException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Role card: the expression layer of the assistant.
 *
 * <p>Six structured blocks mirror how the community personas are actually written (appearance,
 * personality, rhythm, relationship, emotion, boundaries) instead of one blob of prose, so the admin
 * page can edit them piece by piece and a plugin can patch a single block.</p>
 *
 * <p>A persona never carries permissions: it only produces {@link PromptLayer#PERSONA} sections.</p>
 */
public record PersonaCard(
        String code,
        String name,
        String description,
        String appearance,
        String personality,
        String rhythm,
        String relation,
        String emotion,
        String boundary,
        PersonaStyle style,
        List<PersonaExample> examples,
        Map<RelationshipLevel, String> addressRules,
        Set<String> packs,
        boolean enabled,
        boolean defaultPersona,
        int version
) {

    public PersonaCard {
        code = code == null ? null : code.strip();
        name = name == null ? null : name.strip();
        description = description == null ? "" : description.strip();
        style = style == null ? PersonaStyle.empty() : style;
        examples = examples == null ? List.of() : List.copyOf(examples);
        Map<RelationshipLevel, String> rules = new EnumMap<>(RelationshipLevel.class);
        if (addressRules != null) {
            addressRules.forEach((level, value) -> {
                if (level != null && value != null && !value.isBlank()) {
                    rules.put(level, value.strip());
                }
            });
        }
        addressRules = Map.copyOf(rules);
        packs = packs == null ? Set.of() : Set.copyOf(new TreeSet<>(packs));
        version = Math.max(version, 1);
    }

    /** A neutral fallback used when no persona is bound or the bound one is disabled. */
    public static PersonaCard neutral() {
        return new PersonaCard("neutral", "默认助手", "未绑定人设时的中性表达",
                null, "你是一名可靠的助手，直接回答问题。", "使用简体中文，一到三句为主，简短明确。",
                null, null, "不夸大能力、不编造事实；被问到身份时如实说明。",
                new PersonaStyle(3, 1, false), List.of(), Map.of(),
                Set.of("general", "platform"), true, false, 1);
    }

    public void validate() {
        if (code == null || code.isBlank()) {
            throw invalid("人设 code 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw invalid("人设名称不能为空");
        }
        if (packs.isEmpty()) {
            throw invalid("人设必须声明适用能力包（general / platform）");
        }
        if (sections().isEmpty()) {
            throw invalid("人设至少要有一段落内容");
        }
    }

    /** Whether this persona may be used by the given capability pack. */
    public boolean appliesTo(String packId) {
        return packId == null || packs.contains(packId) || packs.contains("all");
    }

    /**
     * Persona-owned prompt sections.
     *
     * <p>All are overridable: they fill the {@code PERSONA} layer only and never replace the
     * capability or safety blocks contributed by the pack.</p>
     */
    public List<PromptSection> sections() {
        List<PromptSection> sections = new ArrayList<>();
        add(sections, "角色形象", appearance);
        add(sections, "角色性格", personality);
        add(sections, "聊天节奏", rhythm);
        add(sections, "关系与群聊", relation);
        add(sections, "记忆与情绪", emotion);
        add(sections, "真实与边界", boundary);
        add(sections, "称呼规则", describeAddressRules());
        add(sections, "表达风格", style.describe());
        add(sections, "语气示例", describeExamples());
        return List.copyOf(sections);
    }

    private String describeAddressRules() {
        if (addressRules.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (RelationshipLevel level : RelationshipLevel.values()) {
            String rule = addressRules.get(level);
            if (rule == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("；");
            }
            builder.append(level.name()).append("：").append(rule);
        }
        return builder.toString();
    }

    private String describeExamples() {
        StringBuilder builder = new StringBuilder();
        for (PersonaExample example : examples) {
            if (!example.isComplete()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(example.describe());
        }
        if (!builder.isEmpty()) {
            builder.append("\n（示例只示范语气，不要照抄）");
        }
        return builder.toString();
    }

    private static void add(List<PromptSection> sections, String name, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        sections.add(PromptSection.overridableSection(name, PromptLayer.PERSONA, content));
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("PERSONA_INVALID", message);
    }
}
