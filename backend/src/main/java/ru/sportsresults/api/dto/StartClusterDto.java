package ru.sportsresults.api.dto;

import java.time.LocalDateTime;

public record StartClusterDto(
        Long id,
        Long raceId,
        String code,
        String sourceName,
        String displayName,
        int displayOrder,
        LocalDateTime startsAt
) {
}
