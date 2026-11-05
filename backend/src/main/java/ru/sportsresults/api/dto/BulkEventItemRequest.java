package ru.sportsresults.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkEventItemRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String location,
        @NotBlank @Size(max = 64) String timeZone,
        @Size(max = 200) List<@Valid EventStartDefinitionRequest> starts
) {
    public BulkEventItemRequest {
        starts = starts == null ? null : List.copyOf(starts);
    }

    public BulkEventItemRequest(String name, String location, String timeZone) {
        this(name, location, timeZone, null);
    }
}
