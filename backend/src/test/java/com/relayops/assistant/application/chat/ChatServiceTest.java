package com.relayops.assistant.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.agent.application.tool.ToolRegistry;
import com.relayops.agent.contract.CapabilityPack;
import com.relayops.agent.contract.PackDescriptor;
import com.relayops.agent.contract.PromptContext;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ToolContext;
import com.relayops.agent.contract.ToolSpec;
import com.relayops.agent.domain.persona.PersonaCard;
import com.relayops.agent.domain.persona.PersonaStyle;
import com.relayops.agent.domain.prompt.PromptSection;
import com.relayops.assistant.application.memory.ConversationService;
import com.relayops.assistant.application.memory.ConversationStore;
import com.relayops.assistant.application.memory.MemoryService;
import com.relayops.assistant.application.memory.MemoryStore;
import com.relayops.assistant.application.memory.TextSummarizer;
import com.relayops.assistant.application.persona.PersonaDirectory;
import com.relayops.assistant.application.prompt.PromptAssemblyService;
import com.relayops.assistant.domain.StoredPersona;
import com.relayops.assistant.domain.memory.Conversation;
import com.relayops.assistant.domain.memory.ConversationMessage;
import com.relayops.assistant.domain.memory.FactSource;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import com.relayops.assistant.domain.memory.MemorySummary;
import com.relayops.assistant.domain.memory.MessageRole;
import com.relayops.shared.time.BusinessTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChatServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T02:00:00Z");
    private static final BusinessTime TIME =
            new BusinessTime(Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
    private static final ScopeRef USER = ScopeRef.user("qq:user:1");

    private static CapabilityPack pack(String id) {
        return new CapabilityPack() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public PackDescriptor describe() {
                return new PackDescriptor(id, "");
            }

            @Override
            public List<PromptSection> prompt(PromptContext context) {
                return List.of();
            }

            @Override
            public List<ToolSpec> tools(ToolContext context) {
                return List.of();
            }

            @Override
            public boolean supports(ScopeRef scope) {
                return true;
            }
        };
    }

    private static final PersonaCard PERSONA = new PersonaCard("dafeiyu", "大肥鱼", "测试人设",
            null, "嘴硬心软", "一到三句", null, null, null, new PersonaStyle(3, 1, false),
            List.of(), Map.of(), java.util.Set.of("general"), true, false, 1);

    private static ChatService service(FakeConversationStore store, FakeMemoryStore memories,
                                       FakeCompletion completion, List<String> capturedPrompt) {
        ConversationService conversations = new ConversationService(store, new NoSummary(), TIME);
        MemoryService memoryService = new MemoryService(memories, new NoEmbedding(), TIME);
        PromptAssemblyService assembly = new PromptAssemblyService(
                new ToolRegistry(List.of(pack("general"), pack("platform"))));
        PersonaDirectory directory = (scope, packId) -> new StoredPersona(7L, PERSONA);
        return new ChatService(directory, conversations, memoryService, assembly, completion);
    }

    private static ChatService.ChatRequest request(String text) {
        return new ChatService.ChatRequest(USER, "general", "qq:user:1", "小明", text, false, false);
    }

    @Test
    void withoutModelItRecordsTheQuestionAndAnswersWithAHint() {
        FakeConversationStore store = new FakeConversationStore();
        FakeMemoryStore memories = new FakeMemoryStore();
        ChatService service = service(store, memories, new FakeCompletion(false, null), new ArrayList<>());

        ChatService.ChatReply reply = service.chat(request("今天号池怎么样"));

        assertThat(reply.mode()).isEqualTo("UNAVAILABLE");
        assertThat(reply.reply()).isEqualTo(ChatService.NO_MODEL_REPLY);
        assertThat(store.messages).hasSize(1);
        assertThat(store.messages.get(0).role()).isEqualTo(MessageRole.USER);
    }

    @Test
    void withModelItSendsPromptAndMemoryAndStoresBothSides() {
        FakeConversationStore store = new FakeConversationStore();
        FakeMemoryStore memories = new FakeMemoryStore();
        memories.facts.add(new MemoryFact(1L, MemoryNamespace.GENERAL, USER, "preference", "风格",
                "喜欢简短回答", 0.9, FactSource.EXPLICIT, Map.of(), null, null, NOW));
        FakeCompletion completion = new FakeCompletion(true, "简短：一切正常。");
        List<String> prompts = new ArrayList<>();
        ChatService service = service(store, memories, completion, prompts);

        ChatService.ChatReply reply = service.chat(request("今天号池怎么样"));

        assertThat(reply.mode()).isEqualTo("MODEL");
        assertThat(reply.reply()).isEqualTo("简短：一切正常。");
        assertThat(reply.memoryHits()).isEqualTo(1);
        assertThat(reply.memoryKeys()).containsExactly("风格");
        assertThat(store.messages).hasSize(2);
        assertThat(store.messages.get(1).role()).isEqualTo(MessageRole.ASSISTANT);
    }

    @Test
    void platformPackRecallsPlatformFactsOnly() {
        FakeConversationStore store = new FakeConversationStore();
        FakeMemoryStore memories = new FakeMemoryStore();
        ChatService service = service(store, memories, new FakeCompletion(true, "ok"), new ArrayList<>());

        service.chat(new ChatService.ChatRequest(USER, "platform", "qq:user:1", "小明", "号池呢", true, true));

        assertThat(memories.lastNamespace).isEqualTo(MemoryNamespace.PLATFORM);
    }

    private static final class FakeCompletion implements ChatCompletion {

        private final boolean available;
        private final String reply;

        private FakeCompletion(boolean available, String reply) {
            this.available = available;
            this.reply = reply;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public Optional<String> complete(String systemPrompt, String userMessage) {
            return available ? Optional.ofNullable(reply) : Optional.empty();
        }
    }

    private static final class NoSummary implements TextSummarizer {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public Optional<String> summarize(String prompt) {
            return Optional.empty();
        }
    }

    private static final class NoEmbedding implements com.relayops.agent.contract.EmbeddingClient {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public int dimensions() {
            return 4;
        }

        @Override
        public String model() {
            return "none";
        }

        @Override
        public Optional<float[]> embed(String text) {
            return Optional.empty();
        }
    }

    private static final class FakeConversationStore implements ConversationStore {

        private final List<ConversationMessage> messages = new ArrayList<>();
        private long nextId = 1;

        @Override
        public long ensure(ScopeRef scope, String packId, Long personaId, Integer personaVersion, Instant now) {
            return 1L;
        }

        @Override
        public Optional<Conversation> find(long conversationId) {
            return Optional.empty();
        }

        @Override
        public long append(long conversationId, MessageRole role, String speakerId, String speakerName,
                           String content, boolean redacted, Instant now) {
            long id = nextId++;
            messages.add(new ConversationMessage(id, conversationId, id, role, speakerId, speakerName,
                    content, redacted, now));
            return id;
        }

        @Override
        public List<ConversationMessage> recent(long conversationId, int limit) {
            return messages.stream().limit(limit).toList();
        }

        @Override
        public Optional<MemorySummary> latestSummary(long conversationId) {
            return Optional.empty();
        }

        @Override
        public void saveSummary(long conversationId, long uptoMessageId, String summary, Integer tokens,
                                String model, Instant now) {
        }

        @Override
        public List<Long> needingSummary(int minMessages, int limit) {
            return List.of();
        }

        @Override
        public long messageCount(long conversationId) {
            return messages.size();
        }
    }

    private static final class FakeMemoryStore implements MemoryStore {

        private final List<MemoryFact> facts = new ArrayList<>();
        private MemoryNamespace lastNamespace;

        @Override
        public long save(MemoryFact fact) {
            return 1L;
        }

        @Override
        public Optional<MemoryFact> find(long id) {
            return facts.stream().filter(fact -> fact.id() == id).findFirst();
        }

        @Override
        public void delete(long id) {
        }

        @Override
        public List<MemoryFact> recent(MemoryNamespace namespace, ScopeRef scope, int limit) {
            lastNamespace = namespace;
            return facts.stream().limit(limit).toList();
        }

        @Override
        public List<MemoryFact> search(MemoryNamespace namespace, ScopeRef scope, String query, int limit) {
            lastNamespace = namespace;
            return facts.stream().limit(limit).toList();
        }

        @Override
        public List<ScoredFact> similar(MemoryNamespace namespace, ScopeRef scope, String model, int dim,
                                        float[] vector, int limit) {
            return List.of();
        }

        @Override
        public void saveEmbedding(long factId, String model, int dim, float[] vector) {
        }

        @Override
        public void touch(List<Long> factIds, Instant now) {
        }

        @Override
        public int purgeExpired() {
            return 0;
        }
    }
}
