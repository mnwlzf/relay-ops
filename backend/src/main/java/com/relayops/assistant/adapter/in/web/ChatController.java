package com.relayops.assistant.adapter.in.web;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ScopeType;
import com.relayops.assistant.application.chat.ChatService;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.web.ApiResponse;
import java.util.Locale;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat entry point for the admin console.
 *
 * <p>This is the Web ingress; the OneBot ingress will reuse {@link ChatService} unchanged.</p>
 */
@RestController
@RequestMapping("/api/v1/assistant")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatService.ChatReply> chat(@RequestBody ChatRequest request) {
        return ApiResponse.of(chatService.chat(request.toChatRequest()));
    }

    /** One chat turn from the console. */
    public record ChatRequest(String text, String packId, String scopeType, String scopeKey,
                              String userKey, String speakerName, Boolean admin, Boolean platformUser) {

        public ChatService.ChatRequest toChatRequest() {
            ScopeType type;
            try {
                type = scopeType == null || scopeType.isBlank()
                        ? ScopeType.USER : ScopeType.valueOf(scopeType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("CHAT_SCOPE_INVALID", "未知的作用域类型: " + scopeType);
            }
            return new ChatService.ChatRequest(new ScopeRef(type, scopeKey), packId,
                    userKey == null ? "console" : userKey,
                    speakerName == null ? "控制台" : speakerName,
                    text, Boolean.TRUE.equals(admin), Boolean.TRUE.equals(platformUser));
        }
    }
}
