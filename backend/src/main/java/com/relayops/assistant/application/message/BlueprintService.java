package com.relayops.assistant.application.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.relayops.agent.application.tool.ToolRegistry;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ToolContext;
import com.relayops.assistant.adapter.out.persistence.BlueprintRepository;
import com.relayops.assistant.domain.message.Blueprint;
import com.relayops.shared.error.BusinessException;
import com.relayops.shared.error.ResourceNotFoundException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Blueprint CRUD plus dry-run composition. */
@Service
public class BlueprintService {

    private final BlueprintRepository repository;
    private final BlueprintCodec codec;
    private final ToolRegistry toolRegistry;
    private final MessageComposer composer;

    public BlueprintService(BlueprintRepository repository, BlueprintCodec codec,
                            ToolRegistry toolRegistry, MessageComposer composer) {
        this.repository = repository;
        this.codec = codec;
        this.toolRegistry = toolRegistry;
        this.composer = composer;
    }

    public List<Blueprint> list() {
        return repository.findAll();
    }

    public Blueprint get(long id) {
        return repository.get(id);
    }

    public Blueprint byCode(String code) {
        return repository.findByCode(code).orElseThrow(() -> new ResourceNotFoundException(
                "BLUEPRINT_NOT_FOUND", "消息蓝图不存在: " + code));
    }

    public Blueprint create(BlueprintCommand command) {
        Blueprint blueprint = toBlueprint(null, 1, command);
        validate(blueprint);
        return repository.insert(blueprint);
    }

    public Blueprint update(long id, BlueprintCommand command) {
        Blueprint existing = repository.get(id);
        if (!command.code().equals(existing.code())) {
            throw new BusinessException("BLUEPRINT_CODE_IMMUTABLE", "蓝图 code 不允许修改");
        }
        Blueprint blueprint = toBlueprint(existing.id(), existing.version(), command);
        validate(blueprint);
        return repository.update(id, blueprint);
    }

    private Blueprint toBlueprint(Long id, int version, BlueprintCommand command) {
        return new Blueprint(id, command.code(), command.name(), command.packId(), command.personaId(),
                codec.parseSegments(command.segments()), codec.parseOptions(command.options()),
                command.enabled(), version);
    }

    public void delete(long id) {
        repository.get(id);
        repository.delete(id);
    }

    /** Renders the message without sending it: the preview is exactly what would be delivered. */
    public MessageComposer.Composed preview(long id, JsonNode args, ScopeRef scope) {
        Blueprint blueprint = repository.get(id);
        return composer.compose(blueprint, scope, args);
    }

    private void validate(Blueprint blueprint) {
        Set<String> schedulable = new java.util.HashSet<>();
        ToolContext context = new ToolContext(ScopeRef.global(), "scheduler", true, true, Set.of());
        toolRegistry.schedulable(ScopeRef.global(), context).forEach(tool -> schedulable.add(tool.id()));
        codec.validate(blueprint, schedulable::contains);
        if (blueprint.packId() == null || blueprint.packId().isBlank()) {
            throw new BusinessException("BLUEPRINT_INVALID", "蓝图必须声明 packId");
        }
    }

    /** Create/update payload. */
    public record BlueprintCommand(String code, String name, String packId, Long personaId,
                                   JsonNode segments, JsonNode options, Boolean enabled) {

        public BlueprintCommand {
            enabled = enabled == null || enabled;
        }
    }
}
