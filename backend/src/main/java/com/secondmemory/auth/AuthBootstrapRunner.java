package com.secondmemory.auth;

import com.secondmemory.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthBootstrapRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(AuthBootstrapRunner.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;

    public AuthBootstrapRunner(AppUserRepository users, PasswordEncoder passwordEncoder, AuthProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(String... args) {
        if (users.count() > 0) {
            return;
        }
        String username = properties.bootstrap() == null ? null : properties.bootstrap().username();
        String password = properties.bootstrap() == null ? null : properties.bootstrap().password();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("No app_user account exists yet and AUTH_BOOTSTRAP_USERNAME/AUTH_BOOTSTRAP_PASSWORD are not set. "
                    + "Set them once to create the first account, then they can be cleared.");
            return;
        }
        users.create(username.trim(), passwordEncoder.encode(password));
        log.info("Created initial app_user account '{}' from AUTH_BOOTSTRAP_USERNAME. "
                + "You can now clear AUTH_BOOTSTRAP_USERNAME/AUTH_BOOTSTRAP_PASSWORD.", username.trim());
    }
}
