package ru.sportsresults.api.dto;

import java.util.List;

public record EventWithStartsDto(EventDto event, List<RaceDto> starts) {
    public EventWithStartsDto {
        starts = List.copyOf(starts);
    }
}
