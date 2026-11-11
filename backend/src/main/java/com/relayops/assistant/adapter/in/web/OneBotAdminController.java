package com.relayops.assistant.adapter.in.web;

import com.relayops.integration.onebot.OneBotClient;
import com.relayops.integration.onebot.OneBotProperties;
import com.relayops.shared.web.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the QQ bridge configuration plus a connectivity test.
 *
 * <p>Everything here comes from {@code relay-ops.onebot.*}; the page exists so a misconfiguration
 * (missing token, wrong API base, empty whitelist) is visible without reading logs or editing files
 * blindly.</p>
 */
@RestController
@RequestMapping("/api/v1/assistant/onebot")
public class OneBotAdminController {

    private final OneBotProperties properties;
    private final OneBotClient client;

    public OneBotAdminController(OneBotProperties properties, OneBotClient client) {
        this.properties = properties;
        this.client = client;
    }

    @GetMapping("/status")
    public ApiResponse<Status> status() {
        return ApiResponse.of(new Status(
                properties.enabled(),
                properties.usable(),
                properties.apiBaseUrl(),
                !properties.webhookToken().isBlank(),
                !properties.apiToken().isBlank(),
                List.copyOf(properties.allowedGroupIds()),
                List.copyOf(properties.adminUserIds()),
                List.copyOf(properties.alertGroupIds()),
                properties.mentionRequired(),
                properties.maxMessageChars(),
                properties.maxSegments(),
                properties.retryMax(),
                properties.sendInterval().toMillis(),
                hints()));
    }

    /** Sends one message so the operator can see whether the API base/token actually work. */
    @PostMapping("/test")
    public ApiResponse<TestResult> test(@RequestBody TestRequest request) {
        Long groupId = request == null || request.groupId() == null
                ? properties.alertGroupIds().stream().findFirst().orElse(null)
                : request.groupId();
        if (!client.available()) {
            return ApiResponse.of(new TestResult(false, groupId,
                    "OneBot 未启用或配置不完整：需要 RELAY_OPS_ONEBOT_ENABLED=true、webhook token 与 api-base-url"));
        }
        if (groupId == null) {
            return ApiResponse.of(new TestResult(false, null,
                    "没有可用的目标群：请在页面填入群号，或配置 RELAY_OPS_ONEBOT_ALERT_GROUPS"));
        }
        client.sendGroup(groupId, "RelayOps 连通性测试：如果你看到这条消息，说明发送通道已打通。");
        return ApiResponse.of(new TestResult(true, groupId,
                "已提交发送（失败会在服务端日志中以 WARN 记录）"));
    }

    private List<String> hints() {
        List<String> hints = new java.util.ArrayList<>();
        if (!properties.enabled()) {
            hints.add("RELAY_OPS_ONEBOT_ENABLED=false：机器人整体关闭");
        }
        if (properties.webhookToken().isBlank()) {
            hints.add("未设置 RELAY_OPS_ONEBOT_WEBHOOK_TOKEN：NapCat 无法回调，接口会返回 401");
        }
        if (properties.apiBaseUrl().isBlank()) {
            hints.add("未设置 RELAY_OPS_ONEBOT_API_BASE_URL：无法主动发消息");
        }
        if (properties.allowedGroupIds().isEmpty()) {
            hints.add("未设置 RELAY_OPS_ONEBOT_ALLOWED_GROUPS：任何群里 @ 机器人都回应");
        }
        if (properties.alertGroupIds().isEmpty()) {
            hints.add("未设置 RELAY_OPS_ONEBOT_ALERT_GROUPS：告警只发邮件，不推群");
        }
        if (properties.adminUserIds().isEmpty()) {
            hints.add("未设置 RELAY_OPS_ONEBOT_ADMIN_USERS：群里没人能查平台数据");
        }
        return List.copyOf(hints);
    }

    /** Current bridge configuration. */
    public record Status(boolean enabled, boolean usable, String apiBaseUrl,
                         boolean webhookTokenConfigured, boolean apiTokenConfigured,
                         List<Long> allowedGroups, List<Long> adminUsers, List<Long> alertGroups,
                         boolean mentionRequired, int maxMessageChars, int maxSegments,
                         int retryMax, long sendIntervalMillis, List<String> hints) {
    }

    /** Test payload; {@code groupId} defaults to the first alert group. */
    public record TestRequest(Long groupId) {
    }

    /** Test outcome. */
    public record TestResult(boolean submitted, Long groupId, String message) {
    }

}
