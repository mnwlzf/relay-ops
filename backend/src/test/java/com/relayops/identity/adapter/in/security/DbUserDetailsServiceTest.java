package com.relayops.identity.adapter.in.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.relayops.identity.domain.IdentityUser;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class DbUserDetailsServiceTest {

    private static DbUserDetailsService service(IdentityUser user) {
        return new DbUserDetailsService(username -> Optional.ofNullable(user));
    }

    @Test
    void mapsRoleToSpringAuthority() {
        UserDetails details = service(new IdentityUser(1, "admin", "hash", "管理员", "ADMIN", true))
                .loadUserByUsername("admin");

        assertThat(details.getUsername()).isEqualTo("admin");
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(details.isEnabled()).isTrue();
    }

    @Test
    void disabledAccountCannotLogIn() {
        DbUserDetailsService service = service(new IdentityUser(2, "bob", "hash", null, "VIEWER", false));

        assertThatThrownBy(() -> service.loadUserByUsername("bob"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("停用");
    }

    @Test
    void unknownAccountIsRejected() {
        assertThatThrownBy(() -> service(null).loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
