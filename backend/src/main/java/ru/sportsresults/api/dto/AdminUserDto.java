package ru.sportsresults.api.dto;

import ru.sportsresults.domain.AdminUserRole;

import java.time.Instant;

public record AdminUserDto(
        Long id,
        String username,
        String displayName,
        AdminUserRole role,
        boolean active,
        long eventCount,
        Instant createdAt,
        Instant updatedAt
) {
}
