package com.relayops.assistant.adapter.in.onebot;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.integration.onebot.OneBotEvent;
import com.relayops.integration.onebot.OneBotProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class OneBotAccessPolicyTest {

    private static OneBotProperties properties(String allowedGroups, String adminUsers) {
        return new OneBotProperties(true, "token", "http://localhost:3000", "",
                allowedGroups, adminUsers, "200,300", 1000, 5,
                java.time.Duration.ofMillis(500), true, Duration.ofSeconds(5));
    }

    private static OneBotEvent group(long groupId, long userId) {
        return new OneBotEvent("message", "group", groupId, userId, "小明", "hi", 999, true);
    }

    private static OneBotEvent privateMessage(long userId) {
        return new OneBotEvent("message", "private", null, userId, "小明", "hi", 999, false);
    }

    @Test
    void emptyWhitelistAllowsAnyGroup() {
        OneBotAccessPolicy policy = new OneBotAccessPolicy(properties("", ""));

        assertThat(policy.allowed(group(1L, 42L))).isTrue();
        assertThat(policy.allowed(group(99999L, 42L))).isTrue();
    }

    @Test
    void configuredWhitelistIsEnforced() {
        OneBotAccessPolicy policy = new OneBotAccessPolicy(properties("100, 200", ""));

        assertThat(policy.allowed(group(100L, 42L))).isTrue();
        assertThat(policy.allowed(group(200L, 42L))).isTrue();
        assertThat(policy.allowed(group(300L, 42L))).isFalse();
    }

    @Test
    void privateChatIsAlwaysAllowed() {
        OneBotAccessPolicy policy = new OneBotAccessPolicy(properties("100", ""));

        assertThat(policy.allowed(privateMessage(42L))).isTrue();
    }

    @Test
    void adminListDecidesWhoSeesPlatformData() {
        OneBotAccessPolicy policy = new OneBotAccessPolicy(properties("", "42, 43"));

        assertThat(policy.admin(42L)).isTrue();
        assertThat(policy.admin(43L)).isTrue();
        assertThat(policy.admin(44L)).isFalse();
    }

    @Test
    void alertGroupsAreParsedIndependently() {
        assertThat(properties("", "").alertGroupIds()).containsExactly(200L, 300L);
        assertThat(properties("", "").allowedGroupIds()).isEmpty();
    }
}
