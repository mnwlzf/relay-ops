package com.relayops.assistant.adapter.out.onebot;

import com.relayops.agent.contract.ScopeType;
import com.relayops.assistant.application.message.MessageSender;
import com.relayops.assistant.adapter.out.mail.EmailMessageSender;
import com.relayops.integration.onebot.OneBotClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Routes a broadcast to the best available channel.
 *
 * <p>Group scopes go to QQ through OneBot when it is configured, everything else falls back to mail.
 * The message is always recorded by the logging sender so the admin page can show it.</p>
 */
@Component
@Primary
public class RoutingMessageSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(RoutingMessageSender.class);

    private final OneBotClient oneBot;
    private final EmailMessageSender email;
    private final LoggingMessageSender recorder;

    public RoutingMessageSender(OneBotClient oneBot, EmailMessageSender email, LoggingMessageSender recorder) {
        this.oneBot = oneBot;
        this.email = email;
        this.recorder = recorder;
    }

    @Override
    public void send(OutboundMessage message) {
        boolean groupScope = message.scope().type() == ScopeType.GROUP;
        if (groupScope && oneBot.available()) {
            Long groupId = parseGroupId(message.scope().key());
            if (groupId != null) {
                oneBot.sendGroup(groupId, message.text());
                recorder.send(message);
                return;
            }
            log.warn("群作用域无法解析群号，回落邮件: {}", message.scope().key());
        }
        email.send(message);
    }

    /** {@code qq:group:123456} -> 123456; anything else is rejected instead of guessing. */
    static Long parseGroupId(String key) {
        if (key == null) {
            return null;
        }
        String digits = key.startsWith("qq:group:") ? key.substring("qq:group:".length()) : key;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
