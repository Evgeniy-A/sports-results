package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotNull;
import ru.sportsresults.domain.EventPublicationStatus;
import ru.sportsresults.domain.ResultsPublicationStatus;

public record UpdateEventPublicationRequest(
        @NotNull EventPublicationStatus eventPublicationStatus,
        @NotNull ResultsPublicationStatus resultsPublicationStatus
) {
}
