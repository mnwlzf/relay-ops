package com.relayops.notification.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Alerting and mail settings.
 *
 * @param balanceThreshold account balance below which a LOW alert fires; negative disables it
 * @param cooldown         suppression window per account and alert type
 */
@ConfigurationProperties(prefix = "relay-ops.notification")
public record NotificationProperties(Mail mail, Alerts alerts) {

    public NotificationProperties {
        mail = mail == null ? Mail.disabled() : mail;
        alerts = alerts == null ? Alerts.defaults() : alerts;
    }

    /** SMTP settings; disabled unless host and recipient are configured. */
    public record Mail(Boolean enabled, String host, int port, String username, String password,
                       String from, String to, Boolean startTls) {

        public Mail {
            enabled = enabled != null && enabled;
            host = host == null ? "" : host.strip();
            port = port <= 0 ? 587 : port;
            username = username == null ? "" : username.strip();
            password = password == null ? "" : password;
            from = from == null ? "" : from.strip();
            to = to == null ? "" : to.strip();
            startTls = startTls == null || startTls;
        }

        public static Mail disabled() {
            return new Mail(false, "", 587, "", "", "", "", true);
        }

        public boolean usable() {
            return enabled && !host.isBlank() && !to.isBlank();
        }
    }

    /** Alert thresholds and suppression window. */
    public record Alerts(java.math.BigDecimal balanceThreshold, Duration cooldown, Integer staleHours) {

        public Alerts {
            balanceThreshold = balanceThreshold == null ? new java.math.BigDecimal("10") : balanceThreshold;
            cooldown = cooldown == null ? Duration.ofHours(6) : cooldown;
            staleHours = staleHours == null || staleHours <= 0 ? 2 : staleHours;
        }

        public static Alerts defaults() {
            return new Alerts(new java.math.BigDecimal("10"), Duration.ofHours(6), 2);
        }

        public boolean balanceAlertEnabled() {
            return balanceThreshold.signum() >= 0;
        }
    }
}
