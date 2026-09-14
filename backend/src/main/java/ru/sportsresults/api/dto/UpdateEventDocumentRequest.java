package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.sportsresults.domain.EventDocumentType;

public record UpdateEventDocumentRequest(
        @NotNull EventDocumentType type,
        @NotBlank @Size(max = 255) String displayName,
        @PositiveOrZero int displayOrder,
        boolean publicDocument
) {
}
