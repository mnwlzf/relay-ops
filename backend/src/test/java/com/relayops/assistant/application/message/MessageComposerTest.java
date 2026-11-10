package com.relayops.assistant.application.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relayops.agent.application.tool.ToolRegistry;
import com.relayops.agent.contract.CapabilityPack;
import com.relayops.agent.contract.PackDescriptor;
import com.relayops.agent.contract.PromptContext;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.SideEffect;
import com.relayops.agent.contract.ToolAccess;
import com.relayops.agent.contract.ToolContext;
import com.relayops.agent.contract.ToolResult;
import com.relayops.agent.contract.ToolSpec;
import com.relayops.agent.domain.prompt.PromptSection;
import com.relayops.assistant.application.memory.TextSummarizer;
import com.relayops.assistant.domain.message.Blueprint;
import com.relayops.shared.time.BusinessTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MessageComposerTest {

    private static final ScopeRef GROUP = ScopeRef.group("qq:group:100");
    private static final BusinessTime TIME =
            new BusinessTime(Clock.fixed(Instant.parse("2026-11-10T01:30:00Z"), ZoneId.of("Asia/Shanghai")));
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final CapabilityPack PACK = new CapabilityPack() {
        @Override
        public String id() {
            return "general";
        }

        @Override
        public PackDescriptor describe() {
            return new PackDescriptor("演示包", "");
        }

        @Override
        public List<PromptSection> prompt(PromptContext context) {
            return List.of();
        }

        @Override
        public List<ToolSpec> tools(ToolContext context) {
            return List.of(
                    new ToolSpec("demo.clock", "时钟", "", SideEffect.READ_ONLY, true,
                            ToolAccess.everyone(), com.relayops.agent.contract.RedactionPolicy.NONE,
                            Duration.ofSeconds(3), null, invocation -> ToolResult.text("09:30")),
                    new ToolSpec("demo.write", "写操作", "", SideEffect.WRITE, true,
                            ToolAccess.everyone(), com.relayops.agent.contract.RedactionPolicy.NONE,
                            Duration.ofSeconds(3), null, invocation -> ToolResult.text("should not run")));
        }

        @Override
        public boolean supports(ScopeRef scope) {
            return true;
        }
    };

    private static MessageComposer composer() {
        TextSummarizer noModel = new TextSummarizer() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public Optional<String> summarize(String prompt) {
                return Optional.empty();
            }
        };
        return new MessageComposer(new ToolRegistry(List.of(PACK)), noModel, TIME);
    }

    private static Blueprint blueprint(List<Blueprint.Segment> segments) {
        return new Blueprint(1L, "demo", "演示", "general", null, segments,
                Blueprint.Options.defaults(), true, 1);
    }

    private static ObjectNode args(String memo) {
        ObjectNode node = JSON.createObjectNode();
        node.put("memo", memo);
        return node;
    }

    @Test
    void rendersTextAndToolResults() {
        Blueprint blueprint = blueprint(List.of(
                new Blueprint.TextSegment("【{{date}}】你好 {{args.memo}}"),
                new Blueprint.ToolSegment("demo.clock", "clock", null, "系统时间", "DEGRADE", "HIDE"),
                new Blueprint.TextSegment("时钟：{{tools.clock}}")));

        MessageComposer.Composed composed = composer().compose(blueprint, GROUP, args("小明"));

        assertThat(composed.text()).contains("2026-11-10").contains("你好 小明")
                .contains("系统时间\n09:30").contains("时钟：09:30");
        assertThat(composed.degraded()).isFalse();
        assertThat(composed.outcomes()).extracting(MessageComposer.Outcome::status)
                .containsExactly("OK", "OK", "OK");
    }

    @Test
    void unknownToolDegradesInsteadOfFailing() {
        Blueprint blueprint = blueprint(List.of(
                new Blueprint.ToolSegment("demo.missing", null, null, "缺失工具", "DEGRADE", "HIDE"),
                new Blueprint.TextSegment("尾巴")));

        MessageComposer.Composed composed = composer().compose(blueprint, GROUP, args("x"));

        assertThat(composed.degraded()).isTrue();
        assertThat(composed.text()).contains("缺失工具 暂时不可用").contains("尾巴");
        assertThat(composed.outcomes().get(0).status()).isEqualTo("DEGRADED");
    }

    @Test
    void writeToolsNeverReachABroadcast() {
        Blueprint blueprint = blueprint(List.of(
                new Blueprint.ToolSegment("demo.write", null, null, null, "SKIP", "HIDE")));

        MessageComposer.Composed composed = composer().compose(blueprint, GROUP, args("x"));

        assertThat(composed.outcomes().get(0).status()).isEqualTo("SKIPPED");
        assertThat(composed.text()).doesNotContain("should not run");
    }

    @Test
    void unresolvedPlaceholdersStayVisible() {
        Blueprint blueprint = blueprint(List.of(
                new Blueprint.TextSegment("缺参数：{{args.nothing}}")));

        MessageComposer.Composed composed = composer().compose(blueprint, GROUP, args("x"));

        assertThat(composed.text()).contains("{{args.nothing}}");
    }

    @Test
    void personaNoteIsSkippedWithoutAModel() {
        Blueprint blueprint = blueprint(List.of(
                new Blueprint.TextSegment("正文"),
                new Blueprint.PersonaNoteSegment("点评一下")));

        MessageComposer.Composed composed = composer().compose(blueprint, GROUP, args("x"));

        assertThat(composed.degraded()).isTrue();
        assertThat(composed.outcomes().get(1).status()).isEqualTo("SKIPPED");
        assertThat(composed.text()).isEqualTo("正文");
    }
}
