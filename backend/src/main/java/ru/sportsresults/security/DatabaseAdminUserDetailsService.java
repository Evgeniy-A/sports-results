package ru.sportsresults.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.AdminUser;
import ru.sportsresults.repository.AdminUserRepository;

@Service
public class DatabaseAdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository repository;

    public DatabaseAdminUserDetailsService(AdminUserRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser user = repository.findByUsernameIgnoreCase(username == null ? "" : username.strip())
                .orElseThrow(() -> new UsernameNotFoundException("Administrative user not found"));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .disabled(!user.isActive())
                .build();
    }
}
