package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.sportsresults.domain.AdminUserRole;

public record CreateAdminUserRequest(
        @NotBlank @Size(max = 160) String username,
        @NotBlank @Size(max = 255) String displayName,
        @NotNull AdminUserRole role,
        @NotBlank @Size(min = 10, max = 200) String password,
        boolean active
) {
}
