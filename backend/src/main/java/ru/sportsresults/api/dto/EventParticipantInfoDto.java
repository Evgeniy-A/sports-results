package ru.sportsresults.api.dto;

import java.math.BigDecimal;

public record EventParticipantInfoDto(
        Long id,
        String shortDescription,
        String venueName,
        String venueAddress,
        String locationDescription,
        BigDecimal latitude,
        BigDecimal longitude,
        String additionalInfo
) {
}
