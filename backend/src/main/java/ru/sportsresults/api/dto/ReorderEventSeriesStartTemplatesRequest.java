package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReorderEventSeriesStartTemplatesRequest(
        @NotEmpty @Size(max = 200) List<@NotNull Long> templateStartIds
) {
    public ReorderEventSeriesStartTemplatesRequest {
        templateStartIds = templateStartIds == null ? null : List.copyOf(templateStartIds);
    }
}
