package ru.sportsresults.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateEventParticipantInfoRequest(
        String shortDescription,
        @Size(max = 255) String venueName,
        @Size(max = 500) String venueAddress,
        String locationDescription,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        String additionalInfo
) {
}
