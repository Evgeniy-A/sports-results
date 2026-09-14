package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.ResultInquiryAvailability;

import java.time.Instant;
import java.time.ZoneId;

@Service
public class ResultInquiryAvailabilityService {

    public ResultInquiryAvailabilityDecision calculate(Event event) {
        return calculate(event, Instant.now());
    }

    public ResultInquiryAvailabilityDecision calculate(Event event, Instant now) {
        if (!event.isResultInquiryEnabled()) {
            return new ResultInquiryAvailabilityDecision(ResultInquiryAvailability.DISABLED, null);
        }
        Integer windowDays = event.getResultInquiryWindowDays();
        if (windowDays == null || windowDays <= 0) {
            throw new IllegalStateException("Enabled result inquiry requires a positive window");
        }
        Instant base = event.getEndsAt() != null ? event.getEndsAt() : event.getStartsAt();
        if (base == null) {
            return new ResultInquiryAvailabilityDecision(ResultInquiryAvailability.NOT_OPEN_YET, null);
        }
        Instant deadline = base.atZone(ZoneId.of(event.getTimeZone())).plusDays(windowDays).toInstant();
        if (now.isBefore(base)) {
            return new ResultInquiryAvailabilityDecision(ResultInquiryAvailability.NOT_OPEN_YET, deadline);
        }
        return new ResultInquiryAvailabilityDecision(
                now.isBefore(deadline) ? ResultInquiryAvailability.OPEN : ResultInquiryAvailability.CLOSED,
                deadline
        );
    }
}
