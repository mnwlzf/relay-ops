package com.relayops.assistant.application.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relayops.agent.application.tool.ToolRegistry;
import com.relayops.agent.contract.CapabilityPack;
import com.relayops.agent.contract.PackDescriptor;
import com.relayops.agent.contract.PromptContext;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ToolContext;
import com.relayops.agent.contract.ToolSpec;
import com.relayops.agent.domain.persona.PersonaCard;
import com.relayops.agent.domain.persona.PersonaStyle;
import com.relayops.agent.domain.prompt.PromptLayer;
import com.relayops.agent.domain.prompt.PromptSection;
import com.relayops.assistant.domain.memory.FactSource;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import com.relayops.shared.error.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PromptAssemblyServiceTest {

    private static final ScopeRef GROUP = ScopeRef.group("qq:group:100");

    private static CapabilityPack pack(String id, List<PromptSection> sections) {
        return new CapabilityPack() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public PackDescriptor describe() {
                return new PackDescriptor(id, "测试能力包");
            }

            @Override
            public List<PromptSection> prompt(PromptContext context) {
                return sections;
            }

            @Override
            public List<ToolSpec> tools(ToolContext context) {
                return List.of();
            }

            @Override
            public boolean supports(ScopeRef scope) {
                return true;
            }
        };
    }

    private static PersonaCard persona(Set<String> packs) {
        return new PersonaCard("dafeiyu", "大肥鱼", "测试人设", "鲸鱼娘", "嘴硬心软", "一到三句",
                null, null, null, new PersonaStyle(3, 1, false), List.of(), Map.of(),
                packs, true, false, 1);
    }

    private static PromptAssemblyService service(CapabilityPack... packs) {
        return new PromptAssemblyService(new ToolRegistry(List.of(packs)));
    }

    @Test
    void safetyComesFirstAndCannotBeOverriddenByAPack() {
        PromptAssemblyService service = service(pack("general", List.of(
                PromptSection.overridableSection("安全边界", PromptLayer.CAPABILITY, "什么都可以做"),
                PromptSection.protectedSection("能力说明", PromptLayer.CAPABILITY, "只读查询"))));

        String rendered = service.assemble("general", persona(Set.of("general")), GROUP, false, false).render();

        assertThat(rendered).startsWith("[安全边界]");
        assertThat(rendered).contains("不编造数字").doesNotContain("什么都可以做");
        assertThat(rendered).contains("[能力说明]").contains("只读查询");
    }

    @Test
    void personaSectionsFollowCapabilityRules() {
        PromptAssemblyService service = service(pack("general", List.of(
                PromptSection.protectedSection("能力说明", PromptLayer.CAPABILITY, "能力规则"))));

        String rendered = service.assemble("general", persona(Set.of("general")), GROUP, false, false).render();

        assertThat(rendered.indexOf("[能力说明]")).isLessThan(rendered.indexOf("[角色性格]"));
        assertThat(rendered).contains("嘴硬心软").contains("每轮最多 3 句");
    }

    @Test
    void personaIsSkippedWhenItDoesNotApplyToThePack() {
        PromptAssemblyService service = service(pack("platform", List.of(
                PromptSection.protectedSection("平台口径", PromptLayer.CAPABILITY, "缓存率=命中/总量"))));

        String rendered = service.assemble("platform", persona(Set.of("general")), GROUP, true, true).render();

        assertThat(rendered).contains("平台口径").doesNotContain("嘴硬心软");
    }

    @Test
    void unknownPackIsRejected() {
        PromptAssemblyService service = service(pack("general", List.of()));

        assertThatThrownBy(() -> service.assemble("platform", persona(Set.of("platform")), GROUP, true, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("能力包不存在");
    }

    @Test
    void previewReportsStructureAndTrimsLowestLayerFirst() {
        PromptAssemblyService service = service(pack("general", List.of(
                PromptSection.protectedSection("能力说明", PromptLayer.CAPABILITY, "能力规则"))));

        PromptAssemblyService.PromptPreview preview = service.preview(
                "general", persona(Set.of("general")), GROUP, false, false, 120);

        assertThat(preview.sections()).extracting(PromptAssemblyService.PromptSectionView::name)
                .containsExactly("安全边界", "能力说明", "角色形象", "角色性格", "聊天节奏", "表达风格");
        assertThat(preview.sections().get(0).overridable()).isFalse();
        assertThat(preview.rendered().length()).isLessThanOrEqualTo(120);
        assertThat(preview.rendered()).contains("安全边界");
    }

    @Test
    void previewUsesDefaultBudgetWhenUnset() {
        PromptAssemblyService service = service(pack("general", List.of()));

        PromptAssemblyService.PromptPreview preview = service.preview(
                "general", persona(Set.of("general")), GROUP, false, false, null);

        assertThat(preview.budget()).isEqualTo(PromptAssemblyService.DEFAULT_BUDGET);
    }

    @Test
    void memoryIsAddedAfterThePersona() {
        PromptAssemblyService service = service(pack("general", List.of(
                PromptSection.protectedSection("能力说明", PromptLayer.CAPABILITY, "能力规则"))));
        MemoryFact fact = new MemoryFact(null, MemoryNamespace.GENERAL, GROUP, "preference", "缓存率",
                "41%", 0.9, FactSource.DERIVED, Map.of(), null, null, Instant.parse("2026-11-01T00:00:00Z"));
        PromptAssemblyService.MemoryPrompt memory =
                new PromptAssemblyService.MemoryPrompt("上次聊到号池", List.of(fact));

        String rendered = service.assemble("general", persona(Set.of("general")), GROUP, false, false, memory)
                .render();

        assertThat(rendered).contains("[会话摘要]").contains("[长期记忆]").contains("- 缓存率：41%");
        assertThat(rendered.indexOf("[会话摘要]")).isGreaterThan(rendered.indexOf("[角色性格]"));
        assertThat(rendered.indexOf("[长期记忆]")).isGreaterThan(rendered.indexOf("[角色性格]"));
    }
}
