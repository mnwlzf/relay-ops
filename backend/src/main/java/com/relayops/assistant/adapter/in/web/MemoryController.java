package com.relayops.assistant.adapter.in.web;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ScopeType;
import com.relayops.assistant.application.memory.MemoryService;
import com.relayops.assistant.domain.memory.FactSource;
import com.relayops.assistant.domain.memory.MemoryFact;
import com.relayops.assistant.domain.memory.MemoryNamespace;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.time.BusinessTime;
import com.relayops.shared.web.ApiResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin API of long-term memory: write, list, forget, recall preview. */
@RestController
@RequestMapping("/api/v1/assistant/memory")
public class MemoryController {

    private final MemoryService memoryService;
    private final BusinessTime time;

    public MemoryController(MemoryService memoryService, BusinessTime time) {
        this.memoryService = memoryService;
        this.time = time;
    }

    @PostMapping("/facts")
    public ApiResponse<MemoryFact> remember(@RequestBody RememberRequest request) {
        return ApiResponse.of(memoryService.remember(request.toFact(time.now())));
    }

    @GetMapping("/facts")
    public ApiResponse<List<MemoryFact>> list(@RequestParam(defaultValue = "GENERAL") String namespace,
                                              @RequestParam(defaultValue = "GLOBAL") String scopeType,
                                              @RequestParam(defaultValue = "*") String scopeKey,
                                              @RequestParam(defaultValue = "20") int limit) {
        ScopeRef scope = new ScopeRef(parseScope(scopeType), scopeKey);
        return ApiResponse.of(memoryService.recent(parseNamespace(namespace), scope, limit));
    }

    @DeleteMapping("/facts/{id}")
    public ApiResponse<Void> forget(@PathVariable long id) {
        memoryService.forget(id);
        return ApiResponse.of(null);
    }

    /** Preview of what the model would receive; also shows whether vectors contributed. */
    @PostMapping("/recall")
    public ApiResponse<MemoryService.Recalled> recall(@RequestBody RecallRequest request) {
        ScopeRef scope = new ScopeRef(parseScope(request.scopeType()), request.scopeKey());
        return ApiResponse.of(memoryService.recall(parseNamespace(request.namespace()), scope,
                request.query(), request.limit() == null ? 5 : request.limit()));
    }

    @PostMapping("/purge")
    public ApiResponse<Integer> purge() {
        return ApiResponse.of(memoryService.purgeExpired());
    }

    private static ScopeType parseScope(String value) {
        try {
            return ScopeType.valueOf(value == null || value.isBlank()
                    ? "GLOBAL" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("MEMORY_SCOPE_INVALID", "未知的作用域类型: " + value);
        }
    }

    private static MemoryNamespace parseNamespace(String value) {
        try {
            return MemoryNamespace.valueOf(value == null || value.isBlank()
                    ? "GENERAL" : value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("MEMORY_NAMESPACE_INVALID", "未知的记忆命名空间: " + value);
        }
    }

    /** Write payload. */
    public record RememberRequest(
            String namespace,
            String scopeType,
            String scopeKey,
            String category,
            String key,
            String value,
            String source,
            Double confidence,
            Integer expiresInDays
    ) {

        public MemoryFact toFact(Instant now) {
            ScopeRef scope = new ScopeRef(parseScope(scopeType), scopeKey);
            FactSource factSource;
            try {
                factSource = source == null || source.isBlank()
                        ? FactSource.EXPLICIT : FactSource.valueOf(source.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("MEMORY_SOURCE_INVALID", "未知的记忆来源: " + source);
            }
            Instant expiresAt = expiresInDays == null || expiresInDays <= 0
                    ? null
                    : now.plus(expiresInDays, ChronoUnit.DAYS);
            return new MemoryFact(null, parseNamespace(namespace), scope, category, key, value,
                    confidence == null ? 0.8 : confidence, factSource, Map.of(), expiresAt, null, now);
        }
    }

    /** Recall preview payload. */
    public record RecallRequest(String namespace, String scopeType, String scopeKey,
                                String query, Integer limit) {
    }
}
