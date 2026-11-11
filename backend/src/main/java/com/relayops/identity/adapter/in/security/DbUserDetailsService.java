package com.relayops.identity.adapter.in.security;

import com.relayops.identity.application.UserDirectory;
import com.relayops.identity.domain.IdentityUser;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads console accounts for form / JSON login. */
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final UserDirectory users;

    public DbUserDetailsService(UserDirectory users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        IdentityUser user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在: " + username));
        if (!user.enabled()) {
            throw new UsernameNotFoundException("账号已停用: " + username);
        }
        return User.withUsername(user.username())
                .password(user.passwordHash())
                .roles(user.role())
                .disabled(!user.enabled())
                .build();
    }
}
