package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.AdminUser;
import ru.sportsresults.domain.AdminUserRole;

import java.util.List;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    long countByRoleAndActiveTrue(AdminUserRole role);
    List<AdminUser> findAllByOrderByDisplayNameAscUsernameAsc();
}
