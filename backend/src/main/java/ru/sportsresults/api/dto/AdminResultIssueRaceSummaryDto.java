package ru.sportsresults.api.dto;

public record AdminResultIssueRaceSummaryDto(
        Long raceId,
        String raceName,
        Long sportFormatId,
        String sportFormatName
) {
}
