package ru.sportsresults.api.dto;

public record CategoryOptionDto(
        Long id,
        Long raceId,
        String raceName,
        String name
) {
}
