package ru.sportsresults.api.dto;

public record ResultInquirySettingsDto(
        boolean enabled,
        Integer windowDays,
        String email
) {
}
