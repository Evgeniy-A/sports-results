package ru.sportsresults.api.dto;

import java.time.Instant;
import java.util.List;

public record PublicAppealEventDto(
        String key,
        String title,
        int year,
        List<City> cities
) {
    public record City(
            String city,
            String eventSlug,
            String eventName,
            Instant eventDate,
            String timeZone
    ) {
    }
}
