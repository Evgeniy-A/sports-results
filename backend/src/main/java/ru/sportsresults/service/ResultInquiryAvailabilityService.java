package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.ResultInquiryAvailability;
import ru.sportsresults.domain.ResultInquiryDeadlineMode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
public class ResultInquiryAvailabilityService {

    public ResultInquiryAvailabilityDecision calculate(Event event) {
        return calculate(event, Instant.now());
    }

    public ResultInquiryAvailabilityDecision calculate(Event event, Instant now) {
        ResultInquiryDeadlineMode mode = event.getResultInquiryDeadlineMode();
        Integer windowDays = event.getResultInquiryWindowDays();
        LocalDate fixedDate = event.getResultInquiryFixedDate();
        if (mode == null) {
            throw new IllegalStateException("Result inquiry deadline mode is required");
        }
        if (event.isResultInquiryEnabled()
                && mode == ResultInquiryDeadlineMode.AFTER_EVENT_DAYS
                && (windowDays == null || windowDays <= 0)) {
            throw new IllegalStateException("Enabled result inquiry requires a positive window");
        }
        if (event.isResultInquiryEnabled()
                && mode == ResultInquiryDeadlineMode.FIXED_DATE
                && fixedDate == null) {
            throw new IllegalStateException("Enabled result inquiry requires a fixed date");
        }
        Instant base = event.getEndsAt() != null ? event.getEndsAt() : event.getStartsAt();
        ZoneId zone = ZoneId.of(event.getTimeZone());
        Instant deadline = calculateDeadline(mode, base, windowDays, fixedDate, zone);
        if (!event.isResultInquiryEnabled()) {
            return new ResultInquiryAvailabilityDecision(ResultInquiryAvailability.DISABLED, deadline);
        }
        if (base == null) {
            return new ResultInquiryAvailabilityDecision(
                    deadline != null && now.isAfter(deadline)
                            ? ResultInquiryAvailability.CLOSED
                            : ResultInquiryAvailability.NOT_OPEN_YET,
                    deadline
            );
        }
        if (now.isBefore(base)) {
            return new ResultInquiryAvailabilityDecision(ResultInquiryAvailability.NOT_OPEN_YET, deadline);
        }
        return new ResultInquiryAvailabilityDecision(
                now.isAfter(deadline) ? ResultInquiryAvailability.CLOSED : ResultInquiryAvailability.OPEN,
                deadline
        );
    }

    private static Instant calculateDeadline(
            ResultInquiryDeadlineMode mode,
            Instant base,
            Integer windowDays,
            LocalDate fixedDate,
            ZoneId zone
    ) {
        LocalDate finalDate;
        if (mode == ResultInquiryDeadlineMode.FIXED_DATE) {
            finalDate = fixedDate;
        } else {
            finalDate = base == null || windowDays == null || windowDays <= 0
                    ? null
                    : base.atZone(zone).toLocalDate().plusDays(windowDays);
        }
        return finalDate == null ? null : finalDate.atTime(LocalTime.MAX).atZone(zone).toInstant();
    }
}
