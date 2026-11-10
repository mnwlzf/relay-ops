package com.relayops.assistant.application.persona;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.domain.persona.PersonaCard;
import com.relayops.agent.domain.persona.PersonaExample;
import com.relayops.agent.domain.persona.PersonaStyle;
import com.relayops.agent.domain.persona.RelationshipLevel;
import com.relayops.assistant.adapter.out.persistence.PersonaRepository;
import com.relayops.assistant.domain.ProfileBinding;
import com.relayops.assistant.domain.StoredPersona;
import com.relayops.shared.error.BusinessException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/** Persona CRUD, scope binding, and resolution to {@link PersonaCard} for the kernel. */
@Service
public class PersonaService implements PersonaDirectory {

    private final PersonaRepository repository;

    public PersonaService(PersonaRepository repository) {
        this.repository = repository;
    }

    public List<StoredPersona> list() {
        return repository.findAll();
    }

    public StoredPersona get(long id) {
        return repository.get(id);
    }

    public StoredPersona create(PersonaCommand command) {
        PersonaCard card = command.toCard(1, false);
        card.validate();
        try {
            return repository.insert(card);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException("PERSONA_CODE_DUPLICATED", "人设 code 已存在: " + card.code());
        }
    }

    public StoredPersona update(long id, PersonaCommand command) {
        StoredPersona existing = repository.get(id);
        PersonaCard card = command.toCard(existing.version(), existing.card().defaultPersona());
        if (!card.code().equals(existing.card().code())) {
            throw new BusinessException("PERSONA_CODE_IMMUTABLE", "人设 code 不允许修改");
        }
        card.validate();
        return repository.update(id, card);
    }

    public void delete(long id) {
        repository.get(id);
        repository.delete(id);
    }

    public StoredPersona setDefault(long id) {
        repository.get(id);
        repository.setDefault(id);
        return repository.get(id);
    }

    public List<ProfileBinding> bindings(String packId) {
        return repository.bindings(packId);
    }

    public ProfileBinding bind(ScopeRef scope, String packId, Long personaId, boolean enabled) {
        if (personaId != null) {
            repository.get(personaId);
        }
        return repository.bind(scope, packId, personaId, enabled);
    }

    /**
     * Resolves the persona for a request.
     *
     * <p>Order: scope binding, then the global binding, then the default persona, then a neutral
     * card. A binding to a disabled or pack-incompatible persona falls through instead of failing.</p>
     */
    public StoredPersona resolve(ScopeRef scope, String packId) {
        List<ProfileBinding> bindings = repository.bindings(packId);
        var personaId = PersonaResolver.resolve(scope, packId, bindings);
        if (personaId.isPresent()) {
            var bound = repository.find(personaId.get())
                    .filter(persona -> persona.card().enabled() && persona.card().appliesTo(packId));
            if (bound.isPresent()) {
                return bound.get();
            }
        }
        return repository.findDefault()
                .filter(persona -> persona.card().enabled() && persona.card().appliesTo(packId))
                .orElseGet(() -> StoredPersona.transientCard(PersonaCard.neutral()));
    }

    /**
     * Admin input of a persona.
     *
     * @param addressRules level name (STRANGER/GROUP_MATE/FRIEND/CLOSE) to addressing style
     */
    public record PersonaCommand(
            String code,
            String name,
            String description,
            String appearance,
            String personality,
            String rhythm,
            String relation,
            String emotion,
            String boundary,
            PersonaStyle style,
            List<PersonaExample> examples,
            Map<String, String> addressRules,
            Set<String> packs,
            Boolean enabled
    ) {

        public PersonaCard toCard(int version, boolean defaultPersona) {
            Map<RelationshipLevel, String> rules = new EnumMap<>(RelationshipLevel.class);
            if (addressRules != null) {
                addressRules.forEach((level, value) -> rules.put(RelationshipLevel.parse(level), value));
            }
            return new PersonaCard(code, name, description, appearance, personality, rhythm, relation,
                    emotion, boundary, style, examples, rules,
                    packs == null || packs.isEmpty() ? Set.of("general") : packs,
                    enabled == null || enabled, defaultPersona, version);
        }
    }
}
