package com.relayops.assistant.adapter.in.onebot;

import com.fasterxml.jackson.databind.JsonNode;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.application.chat.ChatService;
import com.relayops.integration.onebot.OneBotClient;
import com.relayops.integration.onebot.OneBotEvent;
import com.relayops.integration.onebot.OneBotProperties;
import com.relayops.shared.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OneBot v11 reverse-HTTP webhook (the QQ ingress).
 *
 * <p>Lives in {@code assistant} because it is a channel, not a protocol: {@code integration.onebot}
 * keeps the protocol client and event model, this class decides what to answer. Authentication is a
 * shared bearer token and fails closed when unconfigured.</p>
 */
@RestController
@RequestMapping("/api/v1/integration/onebot")
public class OneBotWebhookController {

    private static final Logger log = LoggerFactory.getLogger(OneBotWebhookController.class);

    private final OneBotProperties properties;
    private final OneBotClient client;
    private final ChatService chatService;

    public OneBotWebhookController(OneBotProperties properties, OneBotClient client, ChatService chatService) {
        this.properties = properties;
        this.client = client;
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<String> receive(@RequestHeader(value = "Authorization", required = false) String authorization,
                                          @RequestBody JsonNode payload) {
        if (!properties.usable()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("onebot disabled");
        }
        if (!tokenMatches(authorization)) {
            log.warn("OneBot webhook 鉴权失败");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthorized");
        }
        OneBotEvent event = OneBotEvent.parse(payload);
        if (event == null || event.fromSelf()) {
            return ResponseEntity.ok("ignored");
        }
        String text = event.cleanText();
        if (text.isEmpty()) {
            return ResponseEntity.ok("empty");
        }
        if (event.isGroup() && properties.mentionRequired() && !event.atMe()) {
            return ResponseEntity.ok("ignored");
        }
        try {
            reply(event, text);
        } catch (BusinessException ex) {
            reply(event, "这条消息我处理不了：" + ex.getMessage());
        } catch (RuntimeException ex) {
            // Never throw back into NapCat: it would retry the same event.
            log.warn("处理 OneBot 消息失败: reason={}", ex.getMessage());
        }
        return ResponseEntity.ok("ok");
    }

    private void reply(OneBotEvent event, String text) {
        ScopeRef scope = event.isGroup()
                ? ScopeRef.group("qq:group:" + event.groupId())
                : ScopeRef.user("qq:user:" + event.userId());
        ChatService.ChatReply reply = chatService.chat(new ChatService.ChatRequest(
                scope, "general", "qq:" + event.userId(), event.nickname(), text, false, false));
        String outbound = reply.reply() == null || reply.reply().isBlank()
                ? "（没有生成回复）" : reply.reply();
        if (event.isGroup()) {
            client.sendGroup(event.groupId(), outbound);
        } else {
            client.sendPrivate(event.userId(), outbound);
        }
    }

    /** Constant-time comparison so the token cannot be probed by timing. */
    private boolean tokenMatches(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        return MessageDigest.isEqual(
                authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8),
                properties.webhookToken().getBytes(StandardCharsets.UTF_8));
    }
}
