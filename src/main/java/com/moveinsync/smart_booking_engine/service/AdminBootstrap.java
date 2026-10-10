package com.moveinsync.smart_booking_engine.service;

import com.moveinsync.smart_booking_engine.entity.AppUser;
import com.moveinsync.smart_booking_engine.enums.Role;
import com.moveinsync.smart_booking_engine.repository.AppUserRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Development-only bootstrap for the first admin/approver accounts.
 * Enabled only when {@code app.bootstrap.enabled=true} (never in production
 * config) and requires the credentials via environment variables.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String adminUsername;
    private final String adminPassword;
    private final String approverUsername;
    private final String approverPassword;

    public AdminBootstrap(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                          @Value("${app.bootstrap.enabled:false}") boolean enabled,
                          @Value("${APP_BOOTSTRAP_ADMIN_USERNAME:}") String adminUsername,
                          @Value("${APP_BOOTSTRAP_ADMIN_PASSWORD:}") String adminPassword,
                          @Value("${APP_BOOTSTRAP_APPROVER_USERNAME:}") String approverUsername,
                          @Value("${APP_BOOTSTRAP_APPROVER_PASSWORD:}") String approverPassword) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.approverUsername = approverUsername;
        this.approverPassword = approverPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        createIfMissing(adminUsername, adminPassword, Role.ADMIN);
        createIfMissing(approverUsername, approverPassword, Role.APPROVER);
    }

    private void createIfMissing(String username, String password, Role role) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("Bootstrap {} account skipped: set {} username/password env vars to enable it",
                    role, role);
            return;
        }
        Optional<AppUser> existing = appUserRepository.findByUsername(username);
        if (existing.isPresent()) {
            return;
        }
        appUserRepository.save(new AppUser(username, passwordEncoder.encode(password),
                role, null, true));
        log.info("Bootstrap {} account created: {}", role, username);
    }
}
