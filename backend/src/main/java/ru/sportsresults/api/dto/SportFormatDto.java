package ru.sportsresults.api.dto;

public record SportFormatDto(
        Long id,
        Long eventId,
        String code,
        String sourceName,
        String displayName,
        int displayOrder,
        boolean publicVisible
) {
}
