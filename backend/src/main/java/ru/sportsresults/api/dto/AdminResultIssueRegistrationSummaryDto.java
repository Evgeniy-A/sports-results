package ru.sportsresults.api.dto;

public record AdminResultIssueRegistrationSummaryDto(
        Long registrationId,
        String bib,
        String displayName
) {
}
