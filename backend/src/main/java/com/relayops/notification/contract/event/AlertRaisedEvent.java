package com.relayops.notification.contract.event;

/**
 * Raised when an alert was emitted for an account.
 *
 * <p>Other channels (QQ groups, webhooks) subscribe to this instead of being called from
 * {@code notification}, which keeps the dependency pointing from chat/assistant to the contract.</p>
 *
 * @param alertType BALANCE_LOW / COLLECT_FAILED / COLLECT_STALE
 */
public record AlertRaisedEvent(
        long accountId,
        String accountName,
        String alertType,
        String message
) {
}
