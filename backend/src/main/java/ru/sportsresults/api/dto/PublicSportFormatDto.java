package ru.sportsresults.api.dto;

import java.util.List;

public record PublicSportFormatDto(
        Long id,
        String code,
        String sourceName,
        String displayName,
        int displayOrder,
        List<PublicRaceDetailsDto> races
) {
    public PublicSportFormatDto {
        races = List.copyOf(races);
    }
}
