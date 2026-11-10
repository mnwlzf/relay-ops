package com.relayops.copilot.capability.platform;

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
import com.relayops.monitoring.contract.MonitoringQueryPort;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Platform capability pack: read-only answers about accounts and their collection state.
 *
 * <p>Tools go through {@link MonitoringQueryPort}, never through monitoring tables, so the contract
 * stays the only coupling point. Everything here is {@code READ_ONLY} and schedulable, which is what
 * makes the "平台日报" blueprint possible without exposing write actions.</p>
 */
@Component
public class PlatformCapabilityPack implements CapabilityPack {

    private final MonitoringQueryPort monitoring;

    public PlatformCapabilityPack(MonitoringQueryPort monitoring) {
        this.monitoring = monitoring;
    }

    @Override
    public String id() {
        return "platform";
    }

    @Override
    public PackDescriptor describe() {
        return new PackDescriptor("平台能力包", "账号余额、用量与采集状态的只读查询");
    }

    @Override
    public List<PromptSection> prompt(PromptContext context) {
        return List.of(
                PromptSection.protectedSection("能力边界", PromptLayer.CAPABILITY,
                        "只能通过工具读取平台数据，不得编造数字；没有工具可查的内容直接说明查不到。"),
                PromptSection.protectedSection("数据口径", PromptLayer.CAPABILITY,
                        "余额与用量来自最近一次采集快照；今日消耗是当天快照差值，上游重置计数器时视为未知。"),
                PromptSection.overridableSection("表达偏好", PromptLayer.PACK_STYLE,
                        "先给结论，再给关键数字；涉及多个账号时用列表。"));
    }

    @Override
    public List<ToolSpec> tools(ToolContext context) {
        return List.of(overview(), abnormal());
    }

    @Override
    public boolean supports(ScopeRef scope) {
        return true;
    }

    private ToolSpec overview() {
        return new ToolSpec("platform.overview", "平台总览",
                "账号数量、合计余额与合计已用", SideEffect.READ_ONLY, true, ToolAccess.adminsOnly(),
                com.relayops.agent.contract.RedactionPolicy.CHANNEL, Duration.ofSeconds(5), null,
                invocation -> {
                    var accounts = monitoring.accounts();
                    if (accounts.isEmpty()) {
                        return ToolResult.text("还没有采集到任何账号");
                    }
                    var balance = accounts.stream().map(MonitoringQueryPort.AccountState::balance)
                            .filter(java.util.Objects::nonNull)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    var used = accounts.stream().map(MonitoringQueryPort.AccountState::usedQuota)
                            .filter(java.util.Objects::nonNull)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    long abnormal = accounts.stream().filter(account -> !"SUCCESS".equals(account.status())).count();
                    return ToolResult.text("账号 %d 个，合计余额 %s，合计已用 %s，异常 %d 个"
                            .formatted(accounts.size(), balance.toPlainString(), used.toPlainString(), abnormal));
                });
    }

    private ToolSpec abnormal() {
        return new ToolSpec("platform.abnormal", "异常账号",
                "采集失败或状态非成功的账号清单", SideEffect.READ_ONLY, true, ToolAccess.adminsOnly(),
                com.relayops.agent.contract.RedactionPolicy.CHANNEL, Duration.ofSeconds(5), null,
                invocation -> {
                    var rows = monitoring.accounts().stream()
                            .filter(account -> account.status() == null || !"SUCCESS".equals(account.status()))
                            .toList();
                    if (rows.isEmpty()) {
                        return ToolResult.text("");
                    }
                    StringBuilder builder = new StringBuilder();
                    for (var row : rows) {
                        builder.append("- ").append(row.accountName()).append("（")
                                .append(row.platformCode()).append("）：")
                                .append(row.status() == null ? "未采集" : row.status())
                                .append('\n');
                    }
                    return ToolResult.text(builder.toString().strip());
                });
    }
}
