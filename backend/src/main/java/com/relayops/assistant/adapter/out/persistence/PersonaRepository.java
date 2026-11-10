package com.relayops.assistant.adapter.out.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ScopeType;
import com.relayops.agent.domain.persona.PersonaCard;
import com.relayops.agent.domain.persona.PersonaExample;
import com.relayops.agent.domain.persona.PersonaStyle;
import com.relayops.agent.domain.persona.RelationshipLevel;
import com.relayops.assistant.domain.ProfileBinding;
import com.relayops.assistant.domain.StoredPersona;
import com.relayops.shared.error.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** Persistence of role cards and their scope bindings. */
@Repository
public class PersonaRepository {

    private static final TypeReference<List<PersonaExample>> EXAMPLES = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> ADDRESS_RULES = new TypeReference<>() {
    };
    private static final TypeReference<Set<String>> PACKS = new TypeReference<>() {
    };

    private final AssistantPersonaMapper personaMapper;
    private final AssistantProfileMapper profileMapper;
    private final ObjectMapper objectMapper;

    public PersonaRepository(AssistantPersonaMapper personaMapper,
                             AssistantProfileMapper profileMapper,
                             ObjectMapper objectMapper) {
        this.personaMapper = personaMapper;
        this.profileMapper = profileMapper;
        this.objectMapper = objectMapper;
    }

    public List<StoredPersona> findAll() {
        return personaMapper.selectList(Wrappers.<AssistantPersonaEntity>lambdaQuery()
                        .orderByDesc(AssistantPersonaEntity::getIsDefault)
                        .orderByAsc(AssistantPersonaEntity::getName))
                .stream()
                .map(this::toStored)
                .toList();
    }

    public Optional<StoredPersona> find(long id) {
        return Optional.ofNullable(personaMapper.selectById(id)).map(this::toStored);
    }

    public StoredPersona get(long id) {
        return find(id).orElseThrow(() -> new ResourceNotFoundException(
                "PERSONA_NOT_FOUND", "人设不存在: " + id));
    }

    public Optional<StoredPersona> findDefault() {
        return Optional.ofNullable(personaMapper.selectDefault()).map(this::toStored);
    }

    public StoredPersona insert(PersonaCard card) {
        OffsetDateTime now = OffsetDateTime.now();
        AssistantPersonaEntity entity = new AssistantPersonaEntity();
        apply(entity, card);
        entity.setIsDefault(false);
        entity.setVersion(card.version());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        personaMapper.insert(entity);
        return toStored(personaMapper.selectById(entity.getId()));
    }

    public StoredPersona update(long id, PersonaCard card) {
        AssistantPersonaEntity entity = new AssistantPersonaEntity();
        entity.setId(id);
        apply(entity, card);
        entity.setVersion(card.version() + 1);
        entity.setUpdatedAt(OffsetDateTime.now());
        personaMapper.updateById(entity);
        return toStored(personaMapper.selectById(id));
    }

    public void delete(long id) {
        personaMapper.deleteById(id);
    }

    /** Makes {@code id} the only default persona. */
    public void setDefault(long id) {
        personaMapper.update(null, Wrappers.<AssistantPersonaEntity>lambdaUpdate()
                .set(AssistantPersonaEntity::getIsDefault, false)
                .eq(AssistantPersonaEntity::getIsDefault, true));
        AssistantPersonaEntity entity = new AssistantPersonaEntity();
        entity.setId(id);
        entity.setIsDefault(true);
        entity.setUpdatedAt(OffsetDateTime.now());
        personaMapper.updateById(entity);
    }

    public List<ProfileBinding> bindings(String packId) {
        return profileMapper.selectByPack(packId).stream().map(this::toBinding).toList();
    }

    public ProfileBinding bind(ScopeRef scope, String packId, Long personaId, boolean enabled) {
        AssistantProfileEntity existing = profileMapper.selectOne(
                scope.type().name(), scope.key(), packId);
        if (existing == null) {
            AssistantProfileEntity entity = new AssistantProfileEntity();
            entity.setScopeType(scope.type().name());
            entity.setScopeKey(scope.key());
            entity.setPackId(packId);
            entity.setPersonaId(personaId);
            entity.setEnabled(enabled);
            entity.setCreatedAt(OffsetDateTime.now());
            entity.setUpdatedAt(OffsetDateTime.now());
            profileMapper.insert(entity);
            return toBinding(profileMapper.selectById(entity.getId()));
        }
        existing.setPersonaId(personaId);
        existing.setEnabled(enabled);
        existing.setUpdatedAt(OffsetDateTime.now());
        profileMapper.updateById(existing);
        return toBinding(profileMapper.selectById(existing.getId()));
    }

    private void apply(AssistantPersonaEntity entity, PersonaCard card) {
        entity.setCode(card.code());
        entity.setName(card.name());
        entity.setDescription(card.description());
        entity.setAppearancePrompt(card.appearance());
        entity.setPersonalityPrompt(card.personality());
        entity.setRhythmPrompt(card.rhythm());
        entity.setRelationPrompt(card.relation());
        entity.setEmotionPrompt(card.emotion());
        entity.setBoundaryPrompt(card.boundary());
        entity.setStyle(write(card.style()));
        entity.setExamples(write(card.examples()));
        entity.setAddressRules(write(addressRulesAsMap(card.addressRules())));
        entity.setPacks(write(card.packs()));
        entity.setEnabled(card.enabled());
    }

    private StoredPersona toStored(AssistantPersonaEntity entity) {
        Map<RelationshipLevel, String> rules = new EnumMap<>(RelationshipLevel.class);
        Map<String, String> raw = read(entity.getAddressRules(), ADDRESS_RULES, Map.of());
        raw.forEach((key, value) -> rules.put(RelationshipLevel.parse(key), value));
        PersonaCard card = new PersonaCard(
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.getAppearancePrompt(),
                entity.getPersonalityPrompt(),
                entity.getRhythmPrompt(),
                entity.getRelationPrompt(),
                entity.getEmotionPrompt(),
                entity.getBoundaryPrompt(),
                read(entity.getStyle(), PersonaStyle.class, PersonaStyle.empty()),
                read(entity.getExamples(), EXAMPLES, List.of()),
                rules,
                read(entity.getPacks(), PACKS, Set.of("general")),
                Boolean.TRUE.equals(entity.getEnabled()),
                Boolean.TRUE.equals(entity.getIsDefault()),
                entity.getVersion() == null ? 1 : entity.getVersion());
        return new StoredPersona(entity.getId(), card);
    }

    private ProfileBinding toBinding(AssistantProfileEntity entity) {
        return new ProfileBinding(
                entity.getId(),
                new ScopeRef(ScopeType.valueOf(entity.getScopeType()), entity.getScopeKey()),
                entity.getPackId(),
                entity.getPersonaId(),
                Boolean.TRUE.equals(entity.getEnabled()));
    }

    private static Map<String, String> addressRulesAsMap(Map<RelationshipLevel, String> rules) {
        Map<String, String> map = new LinkedHashMap<>();
        rules.forEach((level, value) -> map.put(level.name(), value));
        return map;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("无法序列化人设字段", ex);
        }
    }

    private <T> T read(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            return fallback;
        }
    }
}
