package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultRecalculationOperationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResultRecalculationPreviewDto(
        UUID operationId,
        ResultRecalculationOperationStatus status,
        EventRef event,
        List<RaceRef> races,
        long baseRevision,
        String configurationDigest,
        String planDigest,
        Instant createdAt,
        Instant expiresAt,
        int currentRegistrationCount,
        int minorCount,
        int adultCount,
        int noBirthDateCount,
        int minorSourceCategoryCount,
        int adultCalculatedCategoryCount,
        int adultWithoutCategoryCount,
        int unchangedCategoryCount,
        int changedCategoryCount,
        int blockingCount,
        boolean rankingAffected,
        int rowLimit,
        boolean rowsTruncated,
        List<Row> rows
) {
    public record EventRef(Long id, String name) {
    }

    public record RaceRef(Long id, String name) {
    }

    public record Row(
            Long registrationId,
            String bib,
            String oldEffectiveCategory,
            String newEffectiveCategory,
            String reason,
            String blockingCode
    ) {
    }
}
