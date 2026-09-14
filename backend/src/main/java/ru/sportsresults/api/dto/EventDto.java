package ru.sportsresults.api.dto;

import ru.sportsresults.domain.EventPublicationStatus;
import ru.sportsresults.domain.EventPhase;
import ru.sportsresults.domain.ResultsPublicationStatus;

import java.time.Instant;

public record EventDto(
        Long id,
        Long eventSeriesId,
        String eventSeriesName,
        String eventSeriesSlug,
        String name,
        String slug,
        Instant startsAt,
        Instant endsAt,
        String location,
        String timeZone,
        EventPublicationStatus publicationStatus,
        ResultsPublicationStatus resultsPublicationStatus,
        EventPhase phase,
        String shortDescription,
        String venueName,
        boolean resultsPublished
) {
}
