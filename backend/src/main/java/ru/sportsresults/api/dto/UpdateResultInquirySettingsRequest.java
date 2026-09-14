package ru.sportsresults.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateResultInquirySettingsRequest(
        @NotNull Boolean enabled,
        @Positive Integer windowDays,
        @Email @Size(max = 320) String email
) {
}
