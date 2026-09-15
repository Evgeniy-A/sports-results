package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultsPublicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PublicRaceDto(
        Long id,
        String name,
        String slug,
        BigDecimal distanceMeters,
        Instant startsAt,
        int displayOrder,
        boolean publicVisible,
        List<StartClusterDto> clusters,
        RaceRulesSummaryDto rules,
        ResultsPublicationStatus resultsPublicationStatus,
        boolean resultsPublished
) {
    public PublicRaceDto {
        clusters = List.copyOf(clusters);
    }
}
