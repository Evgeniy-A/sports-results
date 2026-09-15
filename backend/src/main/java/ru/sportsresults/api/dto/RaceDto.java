package ru.sportsresults.api.dto;

import ru.sportsresults.domain.RaceEntryMode;
import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.domain.ResultsPublicationStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record RaceDto(
        Long id,
        Long eventId,
        Long sportFormatId,
        String sportFormatName,
        String sourceCode,
        String name,
        String slug,
        BigDecimal distanceMeters,
        Instant startsAt,
        RaceEntryMode entryMode,
        int displayOrder,
        RankingBasis publicRankingBasis,
        boolean categoryStandingEnabled,
        boolean publicVisible,
        ResultsPublicationStatus resultsPublicationStatus,
        boolean resultsPublished,
        boolean resultRecalculationRequired,
        String effectiveName,
        boolean effectivePublicVisible
) {
}
