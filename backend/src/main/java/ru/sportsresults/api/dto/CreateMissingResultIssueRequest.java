package ru.sportsresults.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateMissingResultIssueRequest(
        @NotBlank @Size(max = 64) String bib,
        Long raceId,
        @NotBlank @Email @Size(max = 320) String contactEmail,
        @NotBlank @Size(max = 4000) String message,
        Instant estimatedStartAt,
        Instant estimatedFinishAt
) {
}
