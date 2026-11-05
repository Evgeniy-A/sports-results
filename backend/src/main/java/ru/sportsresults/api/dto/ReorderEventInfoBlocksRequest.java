package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReorderEventInfoBlocksRequest(
        @NotEmpty List<@NotNull Long> blockIds
) {
    public ReorderEventInfoBlocksRequest {
        blockIds = List.copyOf(blockIds);
    }
}
