package ru.sportsresults.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record EventSeriesStartTemplateDto(
        Long id,
        Long eventSeriesId,
        String name,
        BigDecimal distanceMeters,
        int displayOrder,
        boolean publicVisible,
        EventSeriesStartAwardPolicyTemplateDto awardPolicy,
        List<EventSeriesStartCategoryTemplateDto> categories,
        Instant createdAt,
        Instant updatedAt
) {
}
