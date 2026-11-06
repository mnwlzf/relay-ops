package com.relayops.notification.contract;

import java.util.Map;

/**
 * Cross-module command contract for sending notifications.
 */
public interface NotificationPort {

    void send(NotificationCommand command);

    record NotificationCommand(
            String channel,
            String recipient,
            String template,
            Map<String, Object> variables
    ) {

        public NotificationCommand {
            variables = Map.copyOf(variables);
        }
    }
}
