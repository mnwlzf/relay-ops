package com.relayops.agent.domain.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relayops.agent.domain.prompt.PromptSection;
import com.relayops.shared.error.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersonaCardTest {

    private static PersonaCard dafeiyu() {
        return new PersonaCard(
                "dafeiyu",
                "大肥鱼",
                "嘴硬心软的蓝色小鲸鱼娘",
                "圆脸、大眼睛，深蓝渐变长发，圆滚滚的鲸鱼尾巴",
                "聪明、有点小脾气、贪吃爱面子，但嘴硬心软",
                "一到三句自然短句，不客服开场，不复述问题",
                "群里先判断话题是否与你有关，不强行接每句话",
                "结合最近对话和长期记忆，不编造共同经历",
                "被问到 AI 身份时如实回答；角色不能成为编造事实的理由",
                new PersonaStyle(3, 1, false),
                List.of(new PersonaExample("今天终于把那个 bug 修了", "可以啊，今天赢了它一回。")),
                Map.of(RelationshipLevel.CLOSE, "昵称或亲昵称呼",
                        RelationshipLevel.GROUP_MATE, "昵称"),
                Set.of("general"),
                true,
                false,
                1);
    }

    @Test
    void producesOneSectionPerBoardBlock() {
        PersonaCard card = dafeiyu();

        List<PromptSection> sections = card.sections();

        assertThat(sections).extracting(PromptSection::name)
                .containsExactly("角色形象", "角色性格", "聊天节奏", "关系与群聊", "记忆与情绪", "真实与边界",
                        "称呼规则", "表达风格", "语气示例");
        assertThat(sections).allMatch(PromptSection::overridable);
        assertThat(card.sections().get(7).content()).contains("每轮最多 3 句").contains("不要每轮都用问句结尾");
    }

    @Test
    void blankBlocksAreSimplyAbsent() {
        PersonaCard card = new PersonaCard("mini", "极简", null, null, "只说实话", null, null, null, null,
                null, List.of(), Map.of(), Set.of("general"), true, false, 1);

        assertThat(card.sections()).extracting(PromptSection::name).containsExactly("角色性格");
        assertThat(card.style()).isEqualTo(PersonaStyle.empty());
    }

    @Test
    void validatesIdentityAndPacks() {
        assertThatThrownBy(() -> new PersonaCard("", "名字", null, null, "性格", null, null, null, null,
                null, List.of(), Map.of(), Set.of("general"), true, false, 1).validate())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("code");

        assertThatThrownBy(() -> new PersonaCard("code", " ", null, null, "性格", null, null, null, null,
                null, List.of(), Map.of(), Set.of("general"), true, false, 1).validate())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("名称");

        assertThatThrownBy(() -> new PersonaCard("code", "名字", null, null, "性格", null, null, null, null,
                null, List.of(), Map.of(), Set.of(), true, false, 1).validate())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("能力包");

        assertThatThrownBy(() -> new PersonaCard("code", "名字", null, null, " ", null, null, null, null,
                null, List.of(), Map.of(), Set.of("general"), true, false, 1).validate())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少要有一段落");
    }

    @Test
    void neutralPersonaIsValidAndShared() {
        PersonaCard neutral = PersonaCard.neutral();

        neutral.validate();
        assertThat(neutral.appliesTo("platform")).isTrue();
        assertThat(neutral.appliesTo("general")).isTrue();
    }

    @Test
    void personaOnlyAppliesToDeclaredPacks() {
        PersonaCard card = dafeiyu();

        assertThat(card.appliesTo("general")).isTrue();
        assertThat(card.appliesTo("platform")).isFalse();
    }
}
