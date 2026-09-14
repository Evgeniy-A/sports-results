package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VerifyResultInquiryRequest(
        @NotBlank @Size(max = 64) String bib,
        @NotNull LocalDate birthDate
) {
}
