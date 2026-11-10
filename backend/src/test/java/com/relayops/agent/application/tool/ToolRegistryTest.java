package com.relayops.agent.application.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.agent.contract.CapabilityPack;
import com.relayops.agent.contract.PackDescriptor;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.SideEffect;
import com.relayops.agent.contract.ToolAccess;
import com.relayops.agent.contract.ToolContext;
import com.relayops.agent.contract.ToolResult;
import com.relayops.agent.contract.ToolSpec;
import com.relayops.agent.domain.prompt.PromptSection;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {

    private static final ScopeRef GROUP = ScopeRef.group("qq:group:10086");
    private static final ScopeRef GLOBAL = ScopeRef.global();

    private static final ToolContext ADMIN = new ToolContext(GROUP, "qq:user:1", true, true, Set.of());
    private static final ToolContext ANONYMOUS = new ToolContext(GROUP, "", false, false, Set.of());

    private static ToolSpec tool(String id, SideEffect effect, boolean schedulable, ToolAccess access) {
        return new ToolSpec(id, id, "测试工具", effect, schedulable, access,
                com.relayops.agent.contract.RedactionPolicy.CHANNEL, Duration.ofSeconds(5), null,
                invocation -> ToolResult.text("ok"));
    }

    private static CapabilityPack pack(String id, Set<ScopeRef> scopes, List<ToolSpec> tools) {
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
            public List<PromptSection> prompt(com.relayops.agent.contract.PromptContext context) {
                return List.of();
            }

            @Override
            public List<ToolSpec> tools(ToolContext context) {
                return tools;
            }

            @Override
            public boolean supports(ScopeRef scope) {
                return scopes.contains(scope);
            }
        };
    }

    @Test
    void onlyToolsOfPacksSupportingTheScopeAreVisible() {
        ToolRegistry registry = new ToolRegistry(List.of(
                pack("platform", Set.of(GROUP), List.of(tool("platform.pool", SideEffect.READ_ONLY, true, ToolAccess.adminsOnly()))),
                pack("general", Set.of(GLOBAL, GROUP), List.of(tool("general.weather", SideEffect.READ_ONLY, false, ToolAccess.everyone())))));

        assertThat(registry.available(GROUP, ADMIN)).extracting(ToolSpec::id)
                .containsExactly("platform.pool", "general.weather");
        assertThat(registry.available(GLOBAL, ADMIN)).extracting(ToolSpec::id)
                .containsExactly("general.weather");
    }

    @Test
    void permissionPredicateHidesToolsFromUnknownUsers() {
        ToolRegistry registry = new ToolRegistry(List.of(
                pack("platform", Set.of(GROUP), List.of(
                        tool("platform.overview", SideEffect.READ_ONLY, true, ToolAccess.adminsOnly()),
                        tool("platform.mine", SideEffect.READ_ONLY, true, ToolAccess.selfOnly())))));

        assertThat(registry.available(GROUP, ADMIN)).extracting(ToolSpec::id)
                .containsExactly("platform.overview", "platform.mine");
        // a QQ user without a platform account sees neither
        assertThat(registry.available(GROUP, ANONYMOUS)).isEmpty();
    }

    @Test
    void schedulableExcludesWritesAndOptedOutTools() {
        ToolRegistry registry = new ToolRegistry(List.of(
                pack("platform", Set.of(GROUP), List.of(
                        tool("platform.read", SideEffect.READ_ONLY, true, ToolAccess.adminsOnly()),
                        tool("platform.readonly-not-for-messages", SideEffect.READ_ONLY, false, ToolAccess.adminsOnly()),
                        tool("platform.recharge", SideEffect.WRITE, true, ToolAccess.adminsOnly())))));

        assertThat(registry.schedulable(GROUP, ADMIN)).extracting(ToolSpec::id)
                .containsExactly("platform.read");
    }

    @Test
    void duplicateIdsKeepTheFirstRegistration() {
        ToolSpec first = new ToolSpec("shared.tool", "第一个", "", SideEffect.READ_ONLY, true,
                ToolAccess.everyone(), com.relayops.agent.contract.RedactionPolicy.NONE, Duration.ofSeconds(5),
                null, invocation -> ToolResult.text("first"));
        ToolSpec second = new ToolSpec("shared.tool", "第二个", "", SideEffect.READ_ONLY, true,
                ToolAccess.everyone(), com.relayops.agent.contract.RedactionPolicy.NONE, Duration.ofSeconds(5),
                null, invocation -> ToolResult.text("second"));

        ToolRegistry registry = new ToolRegistry(List.of(
                pack("a", Set.of(GROUP), List.of(first)),
                pack("b", Set.of(GROUP), List.of(second))));

        List<ToolSpec> tools = registry.available(GROUP, ADMIN);

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).title()).isEqualTo("第一个");
    }

    @Test
    void findReturnsEmptyForUnknownOrHiddenTools() {
        ToolRegistry registry = new ToolRegistry(List.of(
                pack("platform", Set.of(GROUP), List.of(tool("platform.secret", SideEffect.READ_ONLY, true, ToolAccess.adminsOnly())))));

        assertThat(registry.find("platform.secret", GROUP, ADMIN)).isPresent();
        assertThat(registry.find("platform.secret", GROUP, ANONYMOUS)).isEmpty();
        assertThat(registry.find("platform.unknown", GROUP, ADMIN)).isEmpty();
    }
}
