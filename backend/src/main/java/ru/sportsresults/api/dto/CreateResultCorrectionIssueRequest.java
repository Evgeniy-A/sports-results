package ru.sportsresults.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.sportsresults.domain.ResultCorrectionReason;

import java.time.LocalDate;

public record CreateResultCorrectionIssueRequest(
        @NotNull LocalDate birthDate,
        @NotNull ResultCorrectionReason correctionReason,
        @PositiveOrZero Long claimedGunTimeMs,
        @PositiveOrZero Long claimedChipTimeMs,
        @NotBlank @Email @Size(max = 320) String contactEmail,
        @NotBlank @Size(max = 4000) String message
) {
}
