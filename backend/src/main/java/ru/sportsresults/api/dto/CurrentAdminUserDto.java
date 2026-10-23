package ru.sportsresults.api.dto;

import ru.sportsresults.domain.AdminUserRole;

public record CurrentAdminUserDto(
        Long id,
        String username,
        String displayName,
        AdminUserRole role,
        boolean active
) {
}
