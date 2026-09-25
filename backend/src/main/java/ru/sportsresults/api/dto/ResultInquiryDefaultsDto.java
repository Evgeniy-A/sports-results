package ru.sportsresults.api.dto;

import ru.sportsresults.domain.ResultInquiryDeadlineMode;

import java.time.LocalDate;

public record ResultInquiryDefaultsDto(
        boolean enabled,
        ResultInquiryDeadlineMode deadlineMode,
        Integer windowDays,
        LocalDate fixedDate,
        String email
) {
}
