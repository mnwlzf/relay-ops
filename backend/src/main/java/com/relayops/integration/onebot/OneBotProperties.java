package com.relayops.integration.onebot;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OneBot (NapCat) settings.
 *
 * @param webhookToken       token NapCat must present when pushing events; blank disables the endpoint
 * @param apiBaseUrl         NapCat HTTP API base, e.g. {@code http://napcat:3000}
 * @param allowedGroups      comma separated group ids; empty means "no restriction"
 * @param adminUsers         comma separated QQ ids treated as platform admins inside QQ
 * @param alertGroups        comma separated group ids that receive proactive alerts
 * @param maxMessageChars    soft limit per outbound message chunk
 * @param maxSegments        hard cap on chunks per message
 * @param sendInterval       minimum gap between two outbound messages, protects the QQ account
 * @param groupMentionRequired whether to ignore group messages that do not @ the bot
 */
@ConfigurationProperties(prefix = "relay-ops.onebot")
public record OneBotProperties(
        Boolean enabled,
        String webhookToken,
        String apiBaseUrl,
        String apiToken,
        String allowedGroups,
        String adminUsers,
        String alertGroups,
        Integer maxMessageChars,
        Integer maxSegments,
        Duration sendInterval,
        Boolean groupMentionRequired,
        Duration timeout
) {

    public OneBotProperties {
        enabled = enabled != null && enabled;
        webhookToken = webhookToken == null ? "" : webhookToken.strip();
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "" : apiBaseUrl.strip();
        apiToken = apiToken == null ? "" : apiToken.strip();
        allowedGroups = allowedGroups == null ? "" : allowedGroups.strip();
        adminUsers = adminUsers == null ? "" : adminUsers.strip();
        alertGroups = alertGroups == null ? "" : alertGroups.strip();
        maxMessageChars = maxMessageChars == null || maxMessageChars < 50 ? 1000 : maxMessageChars;
        maxSegments = maxSegments == null || maxSegments < 1 ? 5 : Math.min(maxSegments, 10);
        sendInterval = sendInterval == null || sendInterval.isNegative()
                ? Duration.ofMillis(500) : sendInterval;
        groupMentionRequired = groupMentionRequired == null || groupMentionRequired;
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
    }

    /** Both sides must be configured for the bridge to work at all. */
    public boolean usable() {
        return enabled && !webhookToken.isBlank() && !apiBaseUrl.isBlank();
    }

    public boolean mentionRequired() {
        return groupMentionRequired;
    }

    public Set<Long> allowedGroupIds() {
        return parseIds(allowedGroups);
    }

    public Set<Long> adminUserIds() {
        return parseIds(adminUsers);
    }

    public Set<Long> alertGroupIds() {
        return parseIds(alertGroups);
    }

    private static Set<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = new LinkedHashSet<>();
        Arrays.stream(raw.split("\\s*,\\s*"))
                .filter(value -> !value.isBlank())
                .forEach(value -> {
                    try {
                        ids.add(Long.parseLong(value.trim()));
                    } catch (NumberFormatException ignored) {
                        // ignore malformed ids instead of failing startup
                    }
                });
        return ids;
    }
}
