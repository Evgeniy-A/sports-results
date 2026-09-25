package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultInquiryAvailability;
import ru.sportsresults.domain.ResultInquiryDeadlineMode;

import java.time.Instant;
import java.time.LocalDate;

public record ResultInquirySettingsDto(
        boolean enabled,
        ResultInquiryDeadlineMode deadlineMode,
        Integer windowDays,
        LocalDate fixedDate,
        String email,
        ResultInquiryAvailability availability,
        Instant deadline
) {
}
