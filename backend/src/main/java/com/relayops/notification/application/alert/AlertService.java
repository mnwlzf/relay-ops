package com.relayops.notification.application.alert;

import com.relayops.monitoring.contract.MonitoringQueryPort;
import com.relayops.notification.config.NotificationProperties;
import com.relayops.notification.contract.NotificationPort;
import com.relayops.shared.time.BusinessTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Periodic alert sweep: read platform state, apply {@link AlertRules}, deliver and remember.
 *
 * <p>Runs on the scheduler rather than inside collection: an alert burst must not slow down or fail
 * a collection round, and the same sweep also catches accounts that simply stopped being collected.</p>
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final MonitoringQueryPort monitoring;
    private final AlertStore store;
    private final NotificationPort notifications;
    private final NotificationProperties properties;
    private final BusinessTime time;
    private final org.springframework.context.ApplicationEventPublisher events;

    public AlertService(MonitoringQueryPort monitoring, AlertStore store, NotificationPort notifications,
                        NotificationProperties properties, BusinessTime time,
                        org.springframework.context.ApplicationEventPublisher events) {
        this.monitoring = monitoring;
        this.store = store;
        this.notifications = notifications;
        this.properties = properties;
        this.time = time;
        this.events = events;
    }

    /** @return number of alerts delivered */
    public int run() {
        Instant now = time.now();
        Instant staleBefore = now.minus(Duration.ofHours(properties.alerts().staleHours()));
        Map<String, Instant> lastSent = store.lastSent();
        List<AlertRules.Alert> alerts = new ArrayList<>();

        for (MonitoringQueryPort.AccountState account : monitoring.accounts()) {
            Map<AlertRules.AlertType, Instant> perType = new EnumMap<>(AlertRules.AlertType.class);
            for (AlertRules.AlertType type : AlertRules.AlertType.values()) {
                Instant sentAt = lastSent.get(account.accountId() + ":" + type.name());
                if (sentAt != null) {
                    perType.put(type, sentAt);
                }
            }
            var state = new AlertRules.AccountState(account.accountId(), account.accountName(),
                    account.balance(), account.status(), account.collectedAt(), perType);
            alerts.addAll(AlertRules.evaluate(state, properties.alerts().balanceThreshold(),
                    staleBefore, now, properties.alerts().cooldown()));
        }

        for (AlertRules.Alert alert : alerts) {
            notifications.send(new NotificationPort.NotificationCommand("email",
                    properties.mail().to(), "alert",
                    Map.of("subject", subjectOf(alert), "body", bodyOf(alert))));
            store.record(alert.accountId(), alert.type().name(), now, alert.message());
            publish(alert);
        }
        if (!alerts.isEmpty()) {
            log.info("已发送告警: count={}", alerts.size());
        }
        return alerts.size();
    }

    /** Channels such as QQ groups subscribe to this; a broken subscriber must not fail the sweep. */
    private void publish(AlertRules.Alert alert) {
        try {
            events.publishEvent(new com.relayops.notification.contract.event.AlertRaisedEvent(
                    alert.accountId(), alert.accountName(), alert.type().name(), alert.message()));
        } catch (RuntimeException ex) {
            log.warn("发布告警事件失败: account={}, reason={}", alert.accountId(), ex.getMessage());
        }
    }

    private String subjectOf(AlertRules.Alert alert) {        return "[RelayOps] " + alert.accountName() + " · " + label(alert.type());
    }

    private String bodyOf(AlertRules.Alert alert) {
        return "账号：" + alert.accountName() + "（#" + alert.accountId() + "）\n"
                + "类型：" + label(alert.type()) + "\n"
                + "详情：" + alert.message() + "\n"
                + "时间：" + time.now() + "\n\n"
                + "冷却窗口 " + properties.alerts().cooldown().toMinutes() + " 分钟，期间同类告警不重复发送。\n";
    }

    private static String label(AlertRules.AlertType type) {
        return switch (type) {
            case BALANCE_LOW -> "余额偏低";
            case COLLECT_FAILED -> "采集失败";
            case COLLECT_STALE -> "采集沉默";
        };
    }
}
