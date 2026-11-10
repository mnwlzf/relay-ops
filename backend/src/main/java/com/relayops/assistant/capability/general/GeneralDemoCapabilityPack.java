package com.relayops.assistant.capability.general;

import com.relayops.agent.contract.CapabilityPack;
import com.relayops.agent.contract.PackDescriptor;
import com.relayops.agent.contract.PromptContext;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.SideEffect;
import com.relayops.agent.contract.ToolAccess;
import com.relayops.agent.contract.ToolContext;
import com.relayops.agent.contract.ToolResult;
import com.relayops.agent.contract.ToolSpec;
import com.relayops.agent.domain.prompt.PromptLayer;
import com.relayops.agent.domain.prompt.PromptSection;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Placeholder general pack.
 *
 * <p>Exposes two harmless read-only tools so the blueprint dry-run works before the real general
 * capabilities (weather, image, search, voice) and the platform pack land. Replace or extend this
 * class in M7 instead of adding more demo tools here.</p>
 */
@Component
public class GeneralDemoCapabilityPack implements CapabilityPack {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)");

    @Override
    public String id() {
        return "general";
    }

    @Override
    public PackDescriptor describe() {
        return new PackDescriptor("常规能力包（演示）",
                "对话与轻量工具；当前只有 clock / echo 两个演示工具，真实能力随 M7 落地");
    }

    @Override
    public List<PromptSection> prompt(PromptContext context) {
        return List.of(
                PromptSection.protectedSection("能力说明", PromptLayer.CAPABILITY,
                        "当前处于演示阶段，只有 clock 与 echo 两个工具；不要声称能查询平台数据。"),
                PromptSection.overridableSection("表达偏好", PromptLayer.PACK_STYLE,
                        "口语化、简短；不确定时直说不知道。"));
    }

    @Override
    public List<ToolSpec> tools(ToolContext context) {
        return List.of(clock(), echo());
    }

    @Override
    public boolean supports(ScopeRef scope) {
        return true;
    }

    private ToolSpec clock() {
        return new ToolSpec("general.clock", "当前时间", "返回服务器当前时间与时区",
                SideEffect.READ_ONLY, true, ToolAccess.everyone(),
                com.relayops.agent.contract.RedactionPolicy.NONE, Duration.ofSeconds(3),
                null, invocation -> ToolResult.text(
                        ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).format(STAMP)));
    }

    private ToolSpec echo() {
        return new ToolSpec("general.echo", "原样回显", "把 args.text 原样返回，用于验证编排链路",
                SideEffect.READ_ONLY, true, ToolAccess.everyone(),
                com.relayops.agent.contract.RedactionPolicy.NONE, Duration.ofSeconds(3),
                "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}",
                invocation -> {
                    String text = invocation.stringArg("text");
                    return ToolResult.text(text == null || text.isBlank() ? "" : text);
                });
    }
}
