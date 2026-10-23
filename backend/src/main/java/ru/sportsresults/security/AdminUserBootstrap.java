package ru.sportsresults.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.AdminUser;
import ru.sportsresults.domain.AdminUserRole;
import ru.sportsresults.repository.AdminUserRepository;

import java.util.Locale;

@Component
public class AdminUserBootstrap implements ApplicationRunner {

    private final AdminUserRepository repository;
    private final AdminSecurityProperties properties;
    private final PasswordEncoder passwordEncoder;

    public AdminUserBootstrap(
            AdminUserRepository repository,
            AdminSecurityProperties properties,
            PasswordEncoder passwordEncoder
    ) {
        this.repository = repository;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = properties.username().strip().toLowerCase(Locale.ROOT);
        if (repository.existsByUsernameIgnoreCase(username)) {
            return;
        }
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setDisplayName(properties.username().strip());
        user.setRole(AdminUserRole.ORGANIZER);
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        repository.save(user);
    }
}
