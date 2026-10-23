package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAdminUserPasswordRequest(
        @NotBlank @Size(min = 10, max = 200) String password
) {
}
