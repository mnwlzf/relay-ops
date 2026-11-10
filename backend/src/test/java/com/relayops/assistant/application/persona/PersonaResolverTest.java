package com.relayops.assistant.application.persona;

import static org.assertj.core.api.Assertions.assertThat;

import com.relayops.agent.contract.ScopeRef;
import com.relayops.assistant.domain.ProfileBinding;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PersonaResolverTest {

    private static final ScopeRef GROUP = ScopeRef.group("qq:group:100");

    private static ProfileBinding binding(String type, String key, String pack, Long personaId, boolean enabled) {
        ScopeRef scope = switch (type) {
            case "GROUP" -> ScopeRef.group(key);
            case "USER" -> ScopeRef.user(key);
            case "CHANNEL" -> ScopeRef.channel(key);
            default -> ScopeRef.global();
        };
        return new ProfileBinding(1L, scope, pack, personaId, enabled);
    }

    @Test
    void exactScopeBeatsGlobalBinding() {
        List<ProfileBinding> bindings = List.of(
                binding("GLOBAL", "*", "general", 9L, true),
                binding("GROUP", "qq:group:100", "general", 7L, true));

        assertThat(PersonaResolver.resolve(GROUP, "general", bindings)).contains(7L);
    }

    @Test
    void fallsBackToGlobalWhenNoScopeBindingExists() {
        List<ProfileBinding> bindings = List.of(binding("GLOBAL", "*", "general", 9L, true));

        assertThat(PersonaResolver.resolve(GROUP, "general", bindings)).contains(9L);
    }

    @Test
    void ignoresBindingsOfOtherPacks() {
        List<ProfileBinding> bindings = List.of(binding("GROUP", "qq:group:100", "platform", 5L, true));

        assertThat(PersonaResolver.resolve(GROUP, "general", bindings)).isEmpty();
    }

    @Test
    void ignoresDisabledAndUnboundRows() {
        List<ProfileBinding> bindings = List.of(
                binding("GROUP", "qq:group:100", "general", 7L, false),
                binding("GROUP", "qq:group:100", "general", null, true),
                binding("GLOBAL", "*", "general", 9L, true));

        assertThat(PersonaResolver.resolve(GROUP, "general", bindings)).contains(9L);
    }

    @Test
    void returnsEmptyWithoutInput() {
        assertThat(PersonaResolver.resolve(GROUP, "general", List.of())).isEmpty();
        assertThat(PersonaResolver.resolve(null, "general", List.of())).isEmpty();
        assertThat(PersonaResolver.resolve(GROUP, null, List.of())).isEmpty();
    }

    @Test
    void userScopeWorksToo() {
        ScopeRef user = ScopeRef.user("qq:user:1");
        List<ProfileBinding> bindings = List.of(binding("USER", "qq:user:1", "platform", 3L, true));

        Optional<Long> resolved = PersonaResolver.resolve(user, "platform", bindings);

        assertThat(resolved).contains(3L);
    }
}
