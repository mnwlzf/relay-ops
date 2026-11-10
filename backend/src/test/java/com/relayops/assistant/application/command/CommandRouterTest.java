package com.relayops.assistant.application.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.agent.application.model.ModelRouter;
import com.relayops.agent.config.ChatProperties;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.application.chat.ChatService;
import com.relayops.assistant.application.memory.MemoryService;
import com.relayops.assistant.application.memory.MemoryStore;
import com.relayops.assistant.application.message.BlueprintRenderer;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import com.relayops.shared.time.BusinessTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommandRouterTest {

    private static final Instant NOW = Instant.parse("2026-11-10T02:00:00Z");
    private static final BusinessTime TIME =
            new BusinessTime(Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));
    private static final ScopeRef GROUP = ScopeRef.group("qq:group:100");

    private static ChatService.ChatRequest request(String text, boolean admin) {
        return new ChatService.ChatRequest(GROUP, "general", "qq:1", "小明", text, admin, admin);
    }

    private static CommandRouter router(FakeMemoryStore store, String rendered) {
        MemoryService memory = new MemoryService(store, new NoEmbedding(), TIME);
        BlueprintRenderer renderer = (code, scope, args) -> rendered;
        ModelRouter models = new ModelRouter(new ChatProperties(false, ChatProperties.Profile.empty(),
                ChatProperties.Profile.empty()));
        return new CommandRouter(memory, renderer, models, TIME);
    }

    @Test
    void helpsWithTheCommandList() {
        Optional<String> reply = router(new FakeMemoryStore(), "").dispatch(request("/help", true));

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("/remember").contains("/report").contains("/status");
    }

    @Test
    void statusExplainsTheCommandOnlyMode() {
        Optional<String> reply = router(new FakeMemoryStore(), "").dispatch(request("/status", false));

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("模型：").contains("记忆：");
    }

    @Test
    void rememberStoresAFactThroughTheGuard() {
        FakeMemoryStore store = new FakeMemoryStore();

        Optional<String> reply = router(store, "").dispatch(request("/remember 周五不发日报", false));

        assertThat(reply).isPresent();
        assertThat(reply.get()).contains("记下了");
        assertThat(store.saved).hasSize(1);
        assertThat(store.saved.get(0).value()).isEqualTo("周五不发日报");
        assertThat(store.saved.get(0).namespace()).isEqualTo(MemoryNamespace.GENERAL);
    }

    @Test
    void rememberRefusesSecretsAndExplainsWhy() {
        FakeMemoryStore store = new FakeMemoryStore();

        Optional<String> reply = router(store, "")
                .dispatch(request("/remember key is sk-abcdefgh12345678", false));

        assertThat(reply).isPresent();
        assertThat(reply.get()).startsWith("没法记住：");
        assertThat(store.saved).isEmpty();
    }

    @Test
    void reportRendersTheBlueprint() {
        Optional<String> reply = router(new FakeMemoryStore(), "【演示】一切正常")
                .dispatch(request("/report demo.daily-note", true));

        assertThat(reply).contains("【演示】一切正常");
    }

    @Test
    void unknownCommandStillShowsHelp() {
        Optional<String> reply = router(new FakeMemoryStore(), "").dispatch(request("/nope", false));

        assertThat(reply).isPresent();
        assertThat(reply.get()).startsWith("未知命令").contains("/help");
    }

    @Test
    void plainTextIsNotACommand() {
        assertThat(router(new FakeMemoryStore(), "").dispatch(request("今天号池怎么样", false))).isEmpty();
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

    private static final class FakeMemoryStore implements MemoryStore {

        private final List<MemoryFact> saved = new ArrayList<>();
        private long nextId = 10;

        @Override
        public long save(MemoryFact fact) {
            long id = nextId++;
            saved.add(new MemoryFact(id, fact.namespace(), fact.scope(), fact.category(), fact.factKey(),
                    fact.value(), fact.confidence(), fact.source(), fact.provenance(), fact.expiresAt(),
                    fact.lastUsedAt(), fact.createdAt()));
            return id;
        }

        @Override
        public Optional<MemoryFact> find(long id) {
            return saved.stream().filter(fact -> fact.id() == id).findFirst();
        }

        @Override
        public void delete(long id) {
        }

        @Override
        public List<MemoryFact> recent(MemoryNamespace namespace, ScopeRef scope, int limit) {
            return saved;
        }

        @Override
        public List<MemoryFact> search(MemoryNamespace namespace, ScopeRef scope, String query, int limit) {
            return saved;
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
