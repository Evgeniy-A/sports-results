package ru.sportsresults.api.dto;

import jakarta.validation.constraints.Size;

public record RaceResultsDraftRequest(
        @Size(max = 1000) String reason
) {
}
