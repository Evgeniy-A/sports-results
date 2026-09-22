package ru.sportsresults.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record BulkCreateEventsRequest(
        @NotNull Long eventSeriesId,
        @NotNull LocalDate date,
        @NotEmpty @Size(max = 100) List<@Valid BulkEventItemRequest> events,
        @Size(max = 200) List<@Valid EventStartDefinitionRequest> starts
) {
    public BulkCreateEventsRequest {
        events = events == null ? null : List.copyOf(events);
        starts = starts == null ? List.of() : List.copyOf(starts);
    }

    public BulkCreateEventsRequest(Long eventSeriesId, LocalDate date, List<BulkEventItemRequest> events) {
        this(eventSeriesId, date, events, List.of());
    }
}
