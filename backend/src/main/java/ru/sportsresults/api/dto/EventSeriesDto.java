package ru.sportsresults.api.dto;

import java.time.Instant;

public record EventSeriesDto(
        Long id,
        String name,
        String slug,
        String description,
        boolean active,
        long eventCount,
        long startCount,
        ResultInquiryDefaultsDto resultInquiryDefaults,
        Instant createdAt,
        Instant updatedAt
) {
}
