package ru.sportsresults.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateEventWithStartsRequest(
        @NotNull @Valid CreateEventRequest event,
        @NotNull @Size(max = 200) List<@Valid EventStartDefinitionRequest> starts
) {
    public CreateEventWithStartsRequest {
        starts = starts == null ? null : List.copyOf(starts);
    }
}
