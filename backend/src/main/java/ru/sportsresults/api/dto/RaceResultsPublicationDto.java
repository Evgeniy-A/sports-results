package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultsPublicationStatus;

import java.time.Instant;

public record RaceResultsPublicationDto(
        Long eventId,
        Long raceId,
        String raceName,
        ResultsPublicationStatus previousStatus,
        ResultsPublicationStatus currentStatus,
        Instant changedAt
) {
}
