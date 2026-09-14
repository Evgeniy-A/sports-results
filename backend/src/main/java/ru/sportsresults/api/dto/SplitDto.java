package ru.sportsresults.api.dto;

import java.math.BigDecimal;

public record SplitDto(
        Long checkpointId,
        String checkpointCode,
        String checkpointName,
        int sequenceNumber,
        BigDecimal distanceMeters,
        Long gunTimeMs,
        Long chipTimeMs,
        Integer overallPlace,
        Integer genderPlace,
        Integer categoryPlace,
        Integer netOverallPlace,
        Integer netGenderPlace,
        Integer netCategoryPlace
) {
}
