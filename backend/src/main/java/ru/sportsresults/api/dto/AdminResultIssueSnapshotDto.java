package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultIssueSnapshotOrigin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminResultIssueSnapshotDto(
        ResultIssueSnapshotOrigin origin,
        String eventName,
        String eventLocation,
        Instant eventStartsAt,
        Long sportFormatId,
        String sportFormatName,
        String sportFormatCode,
        Long raceId,
        String raceName,
        String raceCode,
        BigDecimal raceDistanceMeters,
        String bib,
        String displayName,
        String effectiveCategoryName,
        String sourceCategory,
        Boolean categoryPubliclyEnabled,
        Long observedGunTimeMs,
        Long observedChipTimeMs,
        String observedResultStatus,
        List<RankingAchievementDto> rankingAchievements,
        Long importBatchId,
        Integer sourceRowNumber
) {
    public AdminResultIssueSnapshotDto {
        if (rankingAchievements != null) {
            rankingAchievements = List.copyOf(rankingAchievements);
        }
    }
}
