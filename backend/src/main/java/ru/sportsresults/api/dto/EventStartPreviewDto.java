package ru.sportsresults.api.dto;

import java.math.BigDecimal;

public record EventStartPreviewDto(
        String name,
        BigDecimal distanceMeters,
        String sourceCode,
        boolean publicVisible,
        boolean awardConfigured
) {
}
