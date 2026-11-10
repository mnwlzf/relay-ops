package com.relayops.assistant.adapter.out.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.assistant.application.message.BlueprintCodec;
import com.relayops.assistant.domain.message.Blueprint;
import com.relayops.shared.error.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/** Persistence of message blueprints. */
@Repository
public class BlueprintRepository {

    private final AssistantBlueprintMapper mapper;
    private final BlueprintCodec codec;
    private final ObjectMapper objectMapper;

    public BlueprintRepository(AssistantBlueprintMapper mapper, BlueprintCodec codec, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.codec = codec;
        this.objectMapper = objectMapper;
    }

    public List<Blueprint> findAll() {
        return mapper.selectList(Wrappers.<AssistantBlueprintEntity>lambdaQuery()
                        .orderByAsc(AssistantBlueprintEntity::getCode))
                .stream().map(this::toBlueprint).toList();
    }

    public Optional<Blueprint> find(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toBlueprint);
    }

    public Optional<Blueprint> findByCode(String code) {
        return Optional.ofNullable(mapper.selectByCode(code)).map(this::toBlueprint);
    }

    public Blueprint get(long id) {
        return find(id).orElseThrow(() -> new ResourceNotFoundException(
                "BLUEPRINT_NOT_FOUND", "消息蓝图不存在: " + id));
    }

    public Blueprint insert(Blueprint blueprint) {
        OffsetDateTime now = OffsetDateTime.now();
        AssistantBlueprintEntity entity = new AssistantBlueprintEntity();
        apply(entity, blueprint);
        entity.setVersion(1);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            mapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw new com.relayops.shared.error.BusinessException(
                    "BLUEPRINT_CODE_DUPLICATED", "蓝图 code 已存在: " + blueprint.code());
        }
        return toBlueprint(mapper.selectById(entity.getId()));
    }

    public Blueprint update(long id, Blueprint blueprint) {
        AssistantBlueprintEntity entity = new AssistantBlueprintEntity();
        entity.setId(id);
        apply(entity, blueprint);
        entity.setVersion(blueprint.version() + 1);
        entity.setUpdatedAt(OffsetDateTime.now());
        mapper.updateById(entity);
        return toBlueprint(mapper.selectById(id));
    }

    public void delete(long id) {
        mapper.deleteById(id);
    }

    private void apply(AssistantBlueprintEntity entity, Blueprint blueprint) {
        entity.setCode(blueprint.code());
        entity.setName(blueprint.name());
        entity.setPackId(blueprint.packId());
        entity.setPersonaId(blueprint.personaId());
        entity.setSegments(write(codec.writeSegments(blueprint.segments())));
        entity.setOptions(write(codec.writeOptions(blueprint.options())));
        entity.setEnabled(blueprint.enabled());
    }

    private Blueprint toBlueprint(AssistantBlueprintEntity entity) {
        return new Blueprint(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getPackId(),
                entity.getPersonaId(),
                codec.parseSegments(read(entity.getSegments())),
                codec.parseOptions(read(entity.getOptions())),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getVersion() == null ? 1 : entity.getVersion());
    }

    private com.fasterxml.jackson.databind.JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("蓝图 JSON 无法解析", ex);
        }
    }

    private String write(com.fasterxml.jackson.databind.JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("蓝图 JSON 无法序列化", ex);
        }
    }
}
