package com.relayops.assistant.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ScopeType;
import com.relayops.assistant.application.message.BlueprintService;
import com.relayops.assistant.application.message.MessageComposer;
import com.relayops.assistant.domain.message.Blueprint;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.web.ApiResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin API of message blueprints plus the dry-run preview. */
@RestController
@RequestMapping("/api/v1/assistant/blueprints")
public class BlueprintController {

    private final BlueprintService service;

    public BlueprintController(BlueprintService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Blueprint>> list() {
        return ApiResponse.of(service.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Blueprint> get(@PathVariable long id) {
        return ApiResponse.of(service.get(id));
    }

    @PostMapping
    public ApiResponse<Blueprint> create(@RequestBody BlueprintRequest request) {
        return ApiResponse.of(service.create(request.toCommand()));
    }

    @PutMapping("/{id}")
    public ApiResponse<Blueprint> update(@PathVariable long id, @RequestBody BlueprintRequest request) {
        return ApiResponse.of(service.update(id, request.toCommand()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ApiResponse.of(null);
    }

    /** Renders the message without sending it. */
    @PostMapping("/{id}/preview")
    public ApiResponse<MessageComposer.Composed> preview(@PathVariable long id,
                                                        @RequestBody(required = false) PreviewRequest request) {
        PreviewRequest body = request == null ? new PreviewRequest(null, null, null) : request;
        return ApiResponse.of(service.preview(id, body.args(), body.toScope()));
    }

    /** Create/update payload. */
    public record BlueprintRequest(String code, String name, String packId, Long personaId,
                                   JsonNode segments, JsonNode options, Boolean enabled) {

        public BlueprintService.BlueprintCommand toCommand() {
            return new BlueprintService.BlueprintCommand(code, name, packId, personaId,
                    segments, options, enabled);
        }
    }

    /** Dry-run payload: args feed the {@code {{args.*}}} placeholders. */
    public record PreviewRequest(JsonNode args, String scopeType, String scopeKey) {

        public ScopeRef toScope() {
            try {
                ScopeType type = scopeType == null || scopeType.isBlank()
                        ? ScopeType.GLOBAL : ScopeType.valueOf(scopeType.trim().toUpperCase(Locale.ROOT));
                return new ScopeRef(type, scopeKey);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("BLUEPRINT_SCOPE_INVALID", "未知的作用域类型: " + scopeType);
            }
        }
    }
}
