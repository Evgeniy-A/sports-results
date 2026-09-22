package ru.sportsresults.api.dto;

import java.time.LocalDate;
import java.util.List;

public record BulkEventPreviewDto(
        Long eventSeriesId,
        String templateName,
        LocalDate date,
        int requestedCount,
        int creatableCount,
        int startsPerEvent,
        int totalStartCount,
        List<EventStartPreviewDto> starts,
        List<Item> events
) {
    public BulkEventPreviewDto {
        starts = List.copyOf(starts);
        events = List.copyOf(events);
    }

    public record Item(
            String name,
            String location,
            String timeZone,
            boolean creatable,
            boolean duplicateInRequest,
            Long existingEventId
    ) {
    }
}
