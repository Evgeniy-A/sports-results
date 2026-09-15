package ru.sportsresults.api.dto;

import java.math.BigDecimal;

public record GlobalResultIssueCurrentContextDto(
        boolean registrationExists,
        boolean registrationRetired,
        Long registrationId,
        CurrentRace race,
        CurrentCategory category,
        CurrentResult result
) {
    public record CurrentRace(Long raceId, String name, String code, BigDecimal distanceMeters) {
    }

    public record CurrentCategory(Long categoryId, String name) {
    }

    public record CurrentResult(Long resultId, String status, Long gunTimeMs, Long chipTimeMs) {
    }
}
