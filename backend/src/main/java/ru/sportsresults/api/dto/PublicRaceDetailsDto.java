package ru.sportsresults.api.dto;

import ru.sportsresults.domain.RaceEntryMode;
import ru.sportsresults.domain.ResultsPublicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PublicRaceDetailsDto(
        Long id,
        String name,
        String slug,
        BigDecimal distanceMeters,
        Instant startsAt,
        RaceEntryMode entryMode,
        int displayOrder,
        List<StartClusterDto> clusters,
        RaceRulesSummaryDto rules,
        ResultsPublicationStatus resultsPublicationStatus,
        boolean resultsPublished
) {
    public PublicRaceDetailsDto {
        clusters = List.copyOf(clusters);
    }
}
