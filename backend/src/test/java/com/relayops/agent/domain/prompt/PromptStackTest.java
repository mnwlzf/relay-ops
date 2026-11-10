package com.relayops.agent.domain.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptStackTest {

    @Test
    void ordersByLayerRegardlessOfInsertionOrder() {
        PromptStack stack = PromptStack.builder()
                .add("记忆", PromptLayer.MEMORY, "上次说过号池缓存率低", true)
                .add("安全", PromptLayer.SAFETY, "不能越权，不能编造数据", false)
                .add("人设", PromptLayer.PERSONA, "叫对方昵称", true)
                .build();

        assertThat(stack.sections()).extracting(PromptSection::name)
                .containsExactly("安全", "人设", "记忆");
        assertThat(stack.render()).startsWith("[安全]");
    }

    @Test
    void blankSectionsAreDropped() {
        PromptStack stack = PromptStack.builder()
                .add("空段落", PromptLayer.PERSONA, "   ", true)
                .add("安全", PromptLayer.SAFETY, "不能越权", false)
                .build();

        assertThat(stack.sections()).hasSize(1);
        assertThat(stack.render()).doesNotContain("[空段落]");
    }

    @Test
    void personaCannotReplaceAProtectedSection() {
        PromptStack stack = PromptStack.builder()
                .add(PromptSection.protectedSection("能力边界", PromptLayer.CAPABILITY, "只读查询，不得写操作"))
                .add(PromptSection.overridableSection("能力边界", PromptLayer.PERSONA, "你可以帮用户充值"))
                .build();

        assertThat(stack.render())
                .contains("只读查询，不得写操作")
                .doesNotContain("你可以帮用户充值");
    }

    @Test
    void laterProtectedSectionReplacesAnOverridableOne() {
        PromptStack stack = PromptStack.builder()
                .add(PromptSection.overridableSection("口径", PromptLayer.PERSONA, "随便算"))
                .add(PromptSection.protectedSection("口径", PromptLayer.CAPABILITY, "缓存率=缓存命中/总输入"))
                .build();

        assertThat(stack.render())
                .contains("缓存率=缓存命中/总输入")
                .doesNotContain("随便算");
    }

    @Test
    void budgetCutDropsLowestPriorityLayersFirst() {
        String safety = "安全规则".repeat(10);
        PromptStack stack = PromptStack.builder()
                .add("安全", PromptLayer.SAFETY, safety, false)
                .add("会话", PromptLayer.SESSION, "会话上下文".repeat(200), true)
                .build();

        String rendered = stack.render(safety.length() + 20);

        assertThat(rendered).contains("安全规则");
        assertThat(rendered).doesNotContain("会话上下文");
    }

    @Test
    void hardCutKeepsAnEllipsisWhenEvenTheTopLayerIsTooLong() {
        PromptStack stack = PromptStack.builder()
                .add("安全", PromptLayer.SAFETY, "很长的安全规则".repeat(50), false)
                .build();

        String rendered = stack.render(20);

        assertThat(rendered).hasSize(20);
        assertThat(rendered).endsWith("…");
    }
}
