package com.relayops.assistant.application.message;

import com.relayops.agent.contract.ScopeRef;

/** Sends a composed message to its destination. */
public interface MessageSender {

    void send(OutboundMessage message);

    /** One message to deliver; the scope already carries the channel key. */
    record OutboundMessage(ScopeRef scope, String blueprintCode, String text) {
    }
}
