package ru.sportsresults.api.dto;

import java.math.BigDecimal;

public record GlobalResultIssueCurrentContextDto(
        boolean registrationExists,
        boolean registrationRetired,
        Long registrationId,
        CurrentSportFormat sportFormat,
        CurrentRace race,
        CurrentCategory category,
        CurrentResult result
) {
    public record CurrentSportFormat(Long sportFormatId, String name, String code) {
    }

    public record CurrentRace(Long raceId, String name, String code, BigDecimal distanceMeters) {
    }

    public record CurrentCategory(Long categoryId, String name) {
    }

    public record CurrentResult(Long resultId, String status, Long gunTimeMs, Long chipTimeMs) {
    }
}
