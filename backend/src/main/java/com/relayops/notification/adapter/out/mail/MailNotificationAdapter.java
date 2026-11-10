package com.relayops.notification.adapter.out.mail;

import com.relayops.notification.config.NotificationProperties;
import com.relayops.notification.contract.NotificationPort;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Mail delivery through a manually configured {@link JavaMailSenderImpl}.
 *
 * <p>Built from {@code relay-ops.notification.mail.*} instead of {@code spring.mail.*} so the module
 * owns its own switch and the application still starts when mail is not configured.</p>
 */
@Component
public class MailNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(MailNotificationAdapter.class);

    private final NotificationProperties properties;
    private final JavaMailSenderImpl sender;

    public MailNotificationAdapter(NotificationProperties properties) {
        this.properties = properties;
        this.sender = properties.mail().usable() ? buildSender(properties.mail()) : null;
        if (sender == null) {
            log.info("邮件通知未启用（relay-ops.notification.mail.enabled=false 或缺少 host/to）");
        }
    }

    public boolean available() {
        return sender != null;
    }

    @Override
    public void send(NotificationCommand command) {
        if (sender == null) {
            log.warn("邮件未配置，跳过通知: {}", command.variables().get("subject"));
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(properties.mail().from().isBlank()
                    ? properties.mail().username() : properties.mail().from());
            helper.setTo(properties.mail().to().split("\\s*,\\s*"));
            helper.setSubject(String.valueOf(command.variables().getOrDefault("subject", "RelayOps 通知")));
            helper.setText(String.valueOf(command.variables().getOrDefault("body", "")), false);
            sender.send(message);
            log.info("邮件通知已发送: recipient={}", properties.mail().to());
        } catch (Exception ex) {
            // Notification failures must never break the caller (collection, alerts, broadcast).
            log.warn("邮件发送失败: reason={}", ex.getMessage());
        }
    }

    /** Convenience for modules that just want a subject/body pair. */
    public void send(String subject, String body) {
        send(new NotificationCommand("email", properties.mail().to(), "text",
                Map.of("subject", subject, "body", body)));
    }

    private static JavaMailSenderImpl buildSender(NotificationProperties.Mail mail) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(mail.host());
        mailSender.setPort(mail.port());
        if (!mail.username().isBlank()) {
            mailSender.setUsername(mail.username());
        }
        if (!mail.password().isBlank()) {
            mailSender.setPassword(mail.password());
        }
        mailSender.setDefaultEncoding("UTF-8");
        Properties javaMail = mailSender.getJavaMailProperties();
        javaMail.put("mail.smtp.auth", String.valueOf(!mail.username().isBlank()));
        javaMail.put("mail.smtp.starttls.enable", String.valueOf(mail.startTls()));
        javaMail.put("mail.smtp.connectiontimeout", "5000");
        javaMail.put("mail.smtp.timeout", "10000");
        return mailSender;
    }
}
