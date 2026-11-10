package com.relayops.assistant.adapter.out.onebot;

import com.relayops.integration.onebot.OneBotClient;
import com.relayops.integration.onebot.OneBotProperties;
import com.relayops.notification.contract.event.AlertRaisedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Pushes alerts into configured QQ groups.
 *
 * <p>Listens to {@link AlertRaisedEvent} instead of being called by {@code notification}: the delivery
 * channel belongs to {@code assistant}, and notification only needs to raise the event.</p>
 */
@Component
public class QqAlertListener {

    private static final Logger log = LoggerFactory.getLogger(QqAlertListener.class);

    private final OneBotClient client;
    private final OneBotProperties properties;

    public QqAlertListener(OneBotClient client, OneBotProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @EventListener
    public void onAlert(AlertRaisedEvent event) {
        if (!client.available() || properties.alertGroupIds().isEmpty()) {
            return;
        }
        String text = format(event);
        for (Long groupId : properties.alertGroupIds()) {
            client.sendGroup(groupId, text);
        }
        log.info("告警已推送到 QQ 群: account={}, groups={}", event.accountId(),
                properties.alertGroupIds().size());
    }

    static String format(AlertRaisedEvent event) {
        String label = switch (event.alertType()) {
            case "BALANCE_LOW" -> "余额偏低";
            case "COLLECT_FAILED" -> "采集失败";
            case "COLLECT_STALE" -> "采集沉默";
            default -> event.alertType();
        };
        return "【" + label + "】" + event.accountName() + "（#" + event.accountId() + "）："
                + event.message();
    }
}
