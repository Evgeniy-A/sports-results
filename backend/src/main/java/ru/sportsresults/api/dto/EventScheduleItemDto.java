package ru.sportsresults.api.dto;

import java.time.LocalDateTime;

public record EventScheduleItemDto(
        Long id,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String title,
        String description,
        int displayOrder
) {
}
