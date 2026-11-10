package com.relayops.integration.onebot;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OneBot (NapCat) settings.
 *
 * @param webhookToken token NapCat must present when pushing events; blank disables the endpoint
 * @param apiBaseUrl   NapCat HTTP API base, e.g. {@code http://napcat:3000}
 * @param apiToken     access token of the NapCat HTTP server, blank when not set
 * @param groupMentionRequired whether to ignore group messages that do not @ the bot
 */
@ConfigurationProperties(prefix = "relay-ops.onebot")
public record OneBotProperties(
        Boolean enabled,
        String webhookToken,
        String apiBaseUrl,
        String apiToken,
        Boolean groupMentionRequired,
        Duration timeout
) {

    public OneBotProperties {
        enabled = enabled != null && enabled;
        webhookToken = webhookToken == null ? "" : webhookToken.strip();
        apiBaseUrl = apiBaseUrl == null || apiBaseUrl.isBlank() ? "" : apiBaseUrl.strip();
        apiToken = apiToken == null ? "" : apiToken.strip();
        groupMentionRequired = groupMentionRequired == null || groupMentionRequired;
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
    }

    /** Both sides must be configured for the bridge to work at all. */
    public boolean usable() {
        return enabled && !webhookToken.isBlank() && !apiBaseUrl.isBlank();
    }

    /** Whether group messages need a mention before the bot answers. */
    public boolean mentionRequired() {
        return groupMentionRequired;
    }
}
