package com.relayops.assistant.application.chat;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.application.memory.ConversationService;
import com.relayops.assistant.application.memory.MemoryService;
import com.relayops.assistant.application.persona.PersonaDirectory;
import com.relayops.assistant.application.prompt.PromptAssemblyService;
import com.relayops.assistant.domain.StoredPersona;
import com.relayops.assistant.domain.memory.ConversationMessage;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import com.relayops.assistant.domain.memory.MemorySummary;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * One chat turn: resolve persona, load memory, assemble the prompt, call the model, persist both
 * sides of the exchange.
 *
 * <p>When no model is configured the turn still records the user message and returns
 * {@code mode = UNAVAILABLE} with a fixed hint, so the caller can answer with commands instead.</p>
 */
@Service
public class ChatService {

    static final String NO_MODEL_REPLY =
            "我暂时没有接上大模型，只能用命令（例如 /平台、/号池）或者管理页面来帮你。";

    private final PersonaDirectory personas;
    private final ConversationService conversations;
    private final MemoryService memory;
    private final PromptAssemblyService promptAssembly;
    private final ChatCompletion completion;

    public ChatService(PersonaDirectory personas, ConversationService conversations, MemoryService memory,
                       PromptAssemblyService promptAssembly, ChatCompletion completion) {
        this.personas = personas;
        this.conversations = conversations;
        this.memory = memory;
        this.promptAssembly = promptAssembly;
        this.completion = completion;
    }

    public ChatReply chat(ChatRequest request) {
        StoredPersona persona = personas.resolve(request.scope(), request.packId());
        long conversationId = conversations.ensure(request.scope(), request.packId(),
                persona.id(), persona.version());
        conversations.appendUser(conversationId, request.userKey(), request.speakerName(), request.text());

        MemoryNamespace namespace = namespaceFor(request.packId());
        MemoryService.Recalled recalled = memory.recall(namespace, request.scope(), request.text(), 5);
        String summary = conversations.latestSummary(conversationId).map(MemorySummary::summary).orElse(null);

        String systemPrompt = promptAssembly.assemble(
                        request.packId(), persona.card(), request.scope(), request.admin(), request.platformUser(),
                        new PromptAssemblyService.MemoryPrompt(summary, recalled.facts()))
                .render();

        Optional<String> reply = completion.available()
                ? completion.complete(systemPrompt, request.text())
                : Optional.empty();
        String mode = reply.isPresent() ? "MODEL" : "UNAVAILABLE";
        String text = reply.orElse(NO_MODEL_REPLY);
        if (reply.isPresent()) {
            conversations.appendAssistant(conversationId, text);
        }
        return new ChatReply(text, mode, recalled.facts().size(), summary != null, conversationId,
                recalled.facts().stream().map(MemoryFact::factKey).filter(java.util.Objects::nonNull).toList(),
                lastMessages(conversationId));
    }

    private List<String> lastMessages(long conversationId) {
        return conversations.recent(conversationId, 4).stream()
                .map(ConversationMessage::asPromptLine)
                .toList();
    }

    /** Platform facts never leak into a general chat turn. */
    private MemoryNamespace namespaceFor(String packId) {
        return "platform".equalsIgnoreCase(packId) ? MemoryNamespace.PLATFORM : MemoryNamespace.GENERAL;
    }

    /**
     * Input of one turn.
     *
     * @param speakerName display name shown in summaries
     * @param userKey     stable identity used for memory scoping
     */
    public record ChatRequest(ScopeRef scope, String packId, String userKey, String speakerName,
                              String text, boolean admin, boolean platformUser) {

        public ChatRequest {
            if (text == null || text.isBlank()) {
                throw new com.relayops.shared.error.BusinessException("CHAT_EMPTY", "消息内容不能为空");
            }
            packId = packId == null || packId.isBlank() ? "general" : packId;
        }
    }

    /** Result of one turn. */
    public record ChatReply(String reply, String mode, int memoryHits, boolean hasSummary,
                            long conversationId, List<String> memoryKeys, List<String> recentLines) {
    }
}
