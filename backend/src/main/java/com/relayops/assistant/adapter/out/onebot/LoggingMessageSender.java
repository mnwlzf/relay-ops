package com.relayops.assistant.adapter.out.onebot;

import com.relayops.assistant.application.message.MessageSender;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Placeholder sender: logs the message and keeps the latest one for the admin page.
 *
 * <p>Replaced by the real OneBot / mail delivery in M7; keeping the port here means the broadcast
 * handler and the composer do not change when delivery lands.</p>
 */
@Component
public class LoggingMessageSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessageSender.class);

    private final AtomicReference<Delivered> last = new AtomicReference<>();

    @Override
    public void send(OutboundMessage message) {
        log.info("待投递消息: scope={}:{}, blueprint={}, chars={}",
                message.scope().type(), message.scope().key(), message.blueprintCode(), message.text().length());
        last.set(new Delivered(message.scope(), message.blueprintCode(), message.text(), Instant.now()));
    }

    /** Last delivered message, for the admin "did it render?" check. */
    public Optional<Delivered> last() {
        return Optional.ofNullable(last.get());
    }

    /** Delivered message snapshot. */
    public record Delivered(com.relayops.agent.contract.ScopeRef scope, String blueprintCode,
                            String text, Instant at) {
    }
}
