package ru.sportsresults.api.dto;

import java.util.List;

public record BulkCreateEventsResponseDto(List<EventDto> events, int totalStartCount) {
    public BulkCreateEventsResponseDto {
        events = List.copyOf(events);
    }
}
