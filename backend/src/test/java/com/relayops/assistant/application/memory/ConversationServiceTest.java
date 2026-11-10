package com.relayops.assistant.application.memory;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.domain.memory.Conversation;
import com.relayops.assistant.domain.memory.ConversationMessage;
import com.relayops.assistant.domain.memory.MemorySummary;
import com.relayops.assistant.domain.memory.MessageRole;
import com.relayops.shared.time.BusinessTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConversationServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T02:00:00Z");
    private static final BusinessTime TIME =
            new BusinessTime(Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
    private static final ScopeRef GROUP = ScopeRef.group("qq:group:100");

    private static ConversationMessage message(long id, MessageRole role, String content) {
        return new ConversationMessage(id, 1L, id, role, "qq:1", "小明", content, false, NOW);
    }

    @Test
    void appendsUsersAndBroadcasts() {
        FakeStore store = new FakeStore();
        ConversationService service = new ConversationService(store, new FakeSummarizer(true), TIME);

        service.appendUser(1L, "qq:1", "小明", "早上好");
        service.appendBroadcast(1L, "号池日报：缓存率 41%");

        assertThat(store.messages).hasSize(2);
        assertThat(store.messages.get(1).role()).isEqualTo(MessageRole.BROADCAST);
    }

    @Test
    void summaryIsSkippedWithoutAModel() {
        FakeStore store = new FakeStore();
        store.messages.add(message(1, MessageRole.USER, "你好"));
        ConversationService service = new ConversationService(store, new FakeSummarizer(false), TIME);

        assertThat(service.summarize(1L, 1)).isEmpty();
        assertThat(store.summaries).isEmpty();
    }

    @Test
    void summaryIsSkippedBeforeTheWindowIsReached() {
        FakeStore store = new FakeStore();
        store.messages.add(message(1, MessageRole.USER, "你好"));
        ConversationService service = new ConversationService(store, new FakeSummarizer(true), TIME);

        assertThat(service.summarize(1L, 10)).isEmpty();
        assertThat(store.summaries).isEmpty();
    }

    @Test
    void summaryCarriesThePreviousSummaryAndAnchorsAtTheLastMessage() {
        FakeStore store = new FakeStore();
        store.messages.add(message(1, MessageRole.USER, "上周说到缓存"));
        store.messages.add(message(2, MessageRole.ASSISTANT, "记下了"));
        store.latest = new MemorySummary(9L, 1L, 8L, "旧摘要：讨论过号池", 20, "gpt-4o-mini", NOW);
        FakeSummarizer summarizer = new FakeSummarizer(true);
        ConversationService service = new ConversationService(store, summarizer, TIME);

        Optional<MemorySummary> saved = service.summarize(1L, 2);

        assertThat(saved).isPresent();
        assertThat(saved.get().summary()).isEqualTo("新的摘要");
        assertThat(summarizer.lastPrompt).contains("旧摘要：讨论过号池").contains("小明：上周说到缓存")
                .contains("不要编造");
        assertThat(store.savedUpto).isEqualTo(2L);
    }

    @Test
    void summarizePendingCountsOnlySuccessfulOnes() {
        FakeStore store = new FakeStore();
        store.pending.add(1L);
        store.pending.add(2L);
        store.messages.add(message(1, MessageRole.USER, "第一段"));
        FakeSummarizer summarizer = new FakeSummarizer(true);
        ConversationService service = new ConversationService(store, summarizer, TIME);

        // conversation 1 has messages, conversation 2 has none -> only one summary
        assertThat(service.summarizePending(1, 10)).isEqualTo(1);
    }

    @Test
    void summaryPromptKeepsSafetyInstructions() {
        String prompt = ConversationService.buildSummaryPrompt("", List.of(
                message(1, MessageRole.USER, "帮我记住周五不发日报")));

        assertThat(prompt)
                .contains("不超过 300 字")
                .contains("不要编造")
                .contains("不要保留密钥")
                .contains("小明：帮我记住周五不发日报")
                .endsWith("只输出摘要正文。");
    }

    private static final class FakeSummarizer implements TextSummarizer {

        private final boolean available;
        private String lastPrompt;

        private FakeSummarizer(boolean available) {
            this.available = available;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public Optional<String> summarize(String prompt) {
            this.lastPrompt = prompt;
            return available ? Optional.of("新的摘要") : Optional.empty();
        }
    }

    private static final class FakeStore implements ConversationStore {

        private final List<ConversationMessage> messages = new ArrayList<>();
        private final List<Long> pending = new ArrayList<>();
        private final Map<Long, MemorySummary> summaries = new LinkedHashMap<>();
        private MemorySummary latest;
        private long savedUpto;
        private long nextId = 100;

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
            return messages.stream()
                    .filter(message -> message.conversationId() == conversationId)
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<MemorySummary> latestSummary(long conversationId) {
            return Optional.ofNullable(latest);
        }

        @Override
        public void saveSummary(long conversationId, long uptoMessageId, String summary, Integer tokens,
                                String model, Instant now) {
            savedUpto = uptoMessageId;
            summaries.put(conversationId,
                    new MemorySummary(1L, conversationId, uptoMessageId, summary, tokens, model, now));
        }

        @Override
        public List<Long> needingSummary(int minMessages, int limit) {
            return pending.stream().limit(limit).toList();
        }

        @Override
        public long messageCount(long conversationId) {
            return messages.size();
        }
    }
}
