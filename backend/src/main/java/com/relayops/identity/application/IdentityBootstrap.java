package com.relayops.identity.application;

import com.relayops.identity.adapter.out.persistence.IdentityUserRepository;
import com.relayops.identity.domain.IdentityUser;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the first console account from environment variables.
 *
 * <p>Deliberately does not invent a default password: without {@code RELAY_OPS_ADMIN_PASSWORD} it
 * logs a warning and leaves login unusable rather than shipping a known credential.</p>
 */
@Component
public class IdentityBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdentityBootstrap.class);

    private final IdentityUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public IdentityBootstrap(IdentityUserRepository repository, PasswordEncoder passwordEncoder,
                             @Value("${relay-ops.identity.admin-username:admin}") String username,
                             @Value("${relay-ops.identity.admin-password:}") String password) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("没有任何控制台账号，且未设置 RELAY_OPS_ADMIN_PASSWORD；"
                    + "登录不可用（可继续用 RELAY_OPS_DEV_OPEN_API=true 临时访问）");
            return;
        }
        repository.insert(username, passwordEncoder.encode(password), "默认管理员", "ADMIN");
        log.warn("已创建默认管理员账号 '{}'，请尽快在数据库中更换密码", username);
    }
}
