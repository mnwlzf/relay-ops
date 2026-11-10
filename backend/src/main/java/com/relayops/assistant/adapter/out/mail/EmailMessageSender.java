package com.relayops.assistant.adapter.out.mail;

import com.relayops.assistant.adapter.out.onebot.LoggingMessageSender;
import com.relayops.assistant.application.message.MessageSender;
import com.relayops.notification.contract.NotificationPort;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Delivers broadcasts through {@link NotificationPort} (mail today, QQ later) and always records the
 * message for the admin page.
 *
 * <p>Lives in {@code assistant} on purpose: the channel module owns delivery, notification only knows
 * how to reach a recipient. That keeps the module rule "business modules must not depend on assistant"
 * intact.</p>
 */
@Component
public class EmailMessageSender implements MessageSender {

    private final NotificationPort notifications;
    private final LoggingMessageSender recorder;

    public EmailMessageSender(NotificationPort notifications, LoggingMessageSender recorder) {
        this.notifications = notifications;
        this.recorder = recorder;
    }

    @Override
    public void send(OutboundMessage message) {
        notifications.send(new NotificationPort.NotificationCommand(
                "email", "*", "broadcast",
                Map.of("subject", "[RelayOps] " + message.blueprintCode(), "body", message.text())));
        recorder.send(message);
    }
}
