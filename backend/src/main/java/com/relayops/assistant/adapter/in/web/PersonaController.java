package com.relayops.assistant.adapter.in.web;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.agent.contract.ScopeType;
import com.relayops.agent.domain.persona.PersonaCard;
import com.relayops.agent.domain.persona.PersonaExample;
import com.relayops.agent.domain.persona.PersonaStyle;
import com.relayops.agent.domain.persona.RelationshipLevel;
import com.relayops.assistant.application.persona.PersonaService;
import com.relayops.assistant.application.prompt.PromptAssemblyService;
import com.relayops.assistant.domain.ProfileBinding;
import com.relayops.assistant.domain.StoredPersona;
import com.relayops.shared.web.ApiResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin API of role cards, scope bindings and prompt preview. */
@RestController
@RequestMapping("/api/v1/assistant")
public class PersonaController {

    private final PersonaService personaService;
    private final PromptAssemblyService promptAssembly;

    public PersonaController(PersonaService personaService, PromptAssemblyService promptAssembly) {
        this.personaService = personaService;
        this.promptAssembly = promptAssembly;
    }

    @GetMapping("/personas")
    public ApiResponse<List<PersonaView>> list() {
        return ApiResponse.of(personaService.list().stream().map(PersonaView::of).toList());
    }

    @GetMapping("/personas/{id}")
    public ApiResponse<PersonaView> get(@PathVariable long id) {
        return ApiResponse.of(PersonaView.of(personaService.get(id)));
    }

    @PostMapping("/personas")
    public ApiResponse<PersonaView> create(@RequestBody PersonaRequest request) {
        return ApiResponse.of(PersonaView.of(personaService.create(request.toCommand())));
    }

    @PutMapping("/personas/{id}")
    public ApiResponse<PersonaView> update(@PathVariable long id, @RequestBody PersonaRequest request) {
        return ApiResponse.of(PersonaView.of(personaService.update(id, request.toCommand())));
    }

    @DeleteMapping("/personas/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        personaService.delete(id);
        return ApiResponse.of(null);
    }

    @PostMapping("/personas/{id}/default")
    public ApiResponse<PersonaView> setDefault(@PathVariable long id) {
        return ApiResponse.of(PersonaView.of(personaService.setDefault(id)));
    }

    @GetMapping("/profiles")
    public ApiResponse<List<ProfileView>> profiles(@RequestParam String packId) {
        return ApiResponse.of(personaService.bindings(packId).stream().map(ProfileView::of).toList());
    }

    /** Creates or updates the binding of one scope to a persona; {@code personaId=null} unbinds it. */
    @PutMapping("/profiles")
    public ApiResponse<ProfileView> bind(@RequestBody ProfileRequest request) {
        ScopeRef scope = request.toScope();
        boolean enabled = request.enabled() == null || request.enabled();
        ProfileBinding binding = personaService.bind(scope, request.packId(), request.personaId(), enabled);
        return ApiResponse.of(ProfileView.of(binding));
    }

    /** Renders exactly what the model would receive for the given pack, persona and scope. */
    @PostMapping("/prompt/preview")
    public ApiResponse<PromptAssemblyService.PromptPreview> preview(@RequestBody PromptPreviewRequest request) {
        ScopeRef scope = request.toScope();
        PersonaCard persona = request.personaId() == null
                ? personaService.resolve(scope, request.packId()).card()
                : personaService.get(request.personaId()).card();
        return ApiResponse.of(promptAssembly.preview(
                request.packId(),
                persona,
                scope,
                Boolean.TRUE.equals(request.admin()),
                Boolean.TRUE.equals(request.platformUser()),
                request.budget()));
    }

    /** Create/update payload of a role card. */
    public record PersonaRequest(
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

        public PersonaService.PersonaCommand toCommand() {
            return new PersonaService.PersonaCommand(code, name, description, appearance, personality,
                    rhythm, relation, emotion, boundary, style, examples, addressRules, packs, enabled);
        }
    }

    /** Read model of a role card. */
    public record PersonaView(
            Long id,
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
            boolean enabled,
            boolean defaultPersona,
            int version
    ) {

        public static PersonaView of(StoredPersona stored) {
            PersonaCard card = stored.card();
            Map<String, String> rules = new LinkedHashMap<>();
            card.addressRules().forEach((level, value) -> rules.put(level.name(), value));
            return new PersonaView(stored.id(), card.code(), card.name(), card.description(),
                    card.appearance(), card.personality(), card.rhythm(), card.relation(), card.emotion(),
                    card.boundary(), card.style(), card.examples(), rules, card.packs(),
                    card.enabled(), card.defaultPersona(), card.version());
        }
    }

    /** Binding of one scope to a persona. */
    public record ProfileRequest(String scopeType, String scopeKey, String packId,
                                 Long personaId, Boolean enabled) {

        public ScopeRef toScope() {
            ScopeType type;
            try {
                type = scopeType == null || scopeType.isBlank()
                        ? ScopeType.GLOBAL
                        : ScopeType.valueOf(scopeType.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new com.relayops.shared.error.BusinessException(
                        "PROFILE_SCOPE_INVALID", "未知的作用域类型: " + scopeType);
            }
            return new ScopeRef(type, scopeKey);
        }
    }

    /** Read model of a scope binding. */
    public record ProfileView(Long id, String scopeType, String scopeKey, String packId,
                              Long personaId, boolean enabled) {

        public static ProfileView of(ProfileBinding binding) {
            return new ProfileView(binding.id(), binding.scope().type().name(), binding.scope().key(),
                    binding.packId(), binding.personaId(), binding.enabled());
        }
    }

    /** Preview payload; omitted {@code personaId} resolves through the binding chain. */
    public record PromptPreviewRequest(String packId, Long personaId, String scopeType, String scopeKey,
                                       Boolean admin, Boolean platformUser, Integer budget) {

        public ScopeRef toScope() {
            return new ProfileRequest(scopeType, scopeKey, packId, null, null).toScope();
        }
    }
}
