package ru.sportsresults.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.sportsresults.domain.ResultInquiryDeadlineMode;

import java.time.LocalDate;

public record UpdateResultInquirySettingsRequest(
        @NotNull Boolean enabled,
        ResultInquiryDeadlineMode deadlineMode,
        @Positive @Max(3650) Integer windowDays,
        LocalDate fixedDate,
        @Email @Size(max = 320) String email
) {
    public UpdateResultInquirySettingsRequest(Boolean enabled, Integer windowDays, String email) {
        this(enabled, ResultInquiryDeadlineMode.AFTER_EVENT_DAYS, windowDays, null, email);
    }
}
