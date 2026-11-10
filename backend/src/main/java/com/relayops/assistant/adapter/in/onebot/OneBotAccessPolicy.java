package com.relayops.assistant.adapter.in.onebot;

import com.relayops.integration.onebot.OneBotEvent;
import com.relayops.integration.onebot.OneBotProperties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Access rules for the QQ channel.
 *
 * <p>Groups are allow-listed: an empty list means "no restriction" for backward compatibility, but a
 * configured list is enforced strictly. Admins are decided by QQ id, independently of the platform
 * account binding, because a group operator may not be a platform admin.</p>
 */
@Component
public class OneBotAccessPolicy {

    private static final Logger log = LoggerFactory.getLogger(OneBotAccessPolicy.class);

    private final OneBotProperties properties;

    public OneBotAccessPolicy(OneBotProperties properties) {
        this.properties = properties;
        if (properties.usable() && properties.allowedGroupIds().isEmpty()) {
            log.warn("未配置 RELAY_OPS_ONEBOT_ALLOWED_GROUPS：机器人会响应任何群里 @ 它的消息");
        }
    }

    public boolean allowed(OneBotEvent event) {
        if (event.isPrivate()) {
            return true;
        }
        Set<Long> allowed = properties.allowedGroupIds();
        if (allowed.isEmpty()) {
            return true;
        }
        return event.groupId() != null && allowed.contains(event.groupId());
    }

    public boolean admin(long userId) {
        return properties.adminUserIds().contains(userId);
    }
}
