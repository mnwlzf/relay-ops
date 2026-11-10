package com.relayops.integration.onebot;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Minimal OneBot v11 message event.
 *
 * @param atMe whether the message mentions this bot, decided at parse time so dispatch stays simple
 */
public record OneBotEvent(
        String postType,
        String messageType,
        Long groupId,
        long userId,
        String nickname,
        String rawMessage,
        long selfId,
        boolean atMe
) {

    public boolean isGroup() {
        return "group".equals(messageType);
    }

    public boolean isPrivate() {
        return "private".equals(messageType);
    }

    /** Messages sent by the bot itself must never be answered. */
    public boolean fromSelf() {
        return userId == selfId;
    }

    /** Parses a webhook payload; returns {@code null} for posts this bridge does not handle. */
    public static OneBotEvent parse(JsonNode root) {
        if (root == null || !"message".equals(root.path("post_type").asText())) {
            return null;
        }
        String messageType = root.path("message_type").asText("");
        if (!"group".equals(messageType) && !"private".equals(messageType)) {
            return null;
        }
        Long groupId = root.hasNonNull("group_id") ? root.get("group_id").asLong() : null;
        long userId = root.path("user_id").asLong(0);
        long selfId = root.path("self_id").asLong(0);
        String nickname = root.path("sender").path("card").asText("");
        if (nickname.isBlank()) {
            nickname = root.path("sender").path("nickname").asText("");
        }
        String raw = root.path("raw_message").asText("");
        return new OneBotEvent("message", messageType, groupId, userId,
                nickname.isBlank() ? String.valueOf(userId) : nickname, raw, selfId,
                mentionsBot(root, selfId));
    }

    private static boolean mentionsBot(JsonNode root, long selfId) {
        String raw = root.path("raw_message").asText("");
        if (raw.contains("[CQ:at,qq=" + selfId)) {
            return true;
        }
        for (JsonNode segment : root.path("message")) {
            if (segment.hasNonNull("type") && "at".equals(segment.get("type").asText())
                    && segment.path("data").path("qq").asText("").equals(String.valueOf(selfId))) {
                return true;
            }
        }
        return false;
    }

    /** Strips CQ at-codes so the model does not see protocol noise. */
    public String cleanText() {
        return rawMessage == null ? "" : rawMessage.replaceAll("\\[CQ:at,qq=\\d+\\]", "").strip();
    }
}
