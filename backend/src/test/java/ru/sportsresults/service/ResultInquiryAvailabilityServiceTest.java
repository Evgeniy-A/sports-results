package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.ResultInquiryAvailability;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ResultInquiryAvailabilityServiceTest {

    private final ResultInquiryAvailabilityService service = new ResultInquiryAvailabilityService();

    @Test
    void returnsDisabledWithoutADeadline() {
        Event event = new Event();

        assertThat(service.calculate(event, Instant.parse("2026-01-01T00:00:00Z")))
                .isEqualTo(new ResultInquiryAvailabilityDecision(ResultInquiryAvailability.DISABLED, null));
    }

    @Test
    void distinguishesNotOpenOpenAndClosed() {
        Event event = configuredEvent("Europe/Moscow", 5);
        event.setEndsAt(Instant.parse("2026-01-10T10:00:00Z"));

        assertThat(service.calculate(event, Instant.parse("2026-01-10T09:59:59Z")).state())
                .isEqualTo(ResultInquiryAvailability.NOT_OPEN_YET);
        assertThat(service.calculate(event, Instant.parse("2026-01-10T10:00:00Z")).state())
                .isEqualTo(ResultInquiryAvailability.OPEN);
        assertThat(service.calculate(event, Instant.parse("2026-01-15T09:59:59Z")).state())
                .isEqualTo(ResultInquiryAvailability.OPEN);
        assertThat(service.calculate(event, Instant.parse("2026-01-15T10:00:00Z")).state())
                .isEqualTo(ResultInquiryAvailability.CLOSED);
    }

    @Test
    void addsCalendarDaysInTheEventTimeZoneAcrossDst() {
        Event event = configuredEvent("America/New_York", 1);
        event.setEndsAt(Instant.parse("2026-03-08T06:30:00Z"));

        assertThat(service.calculate(event, Instant.parse("2026-03-08T07:00:00Z")).deadline())
                .isEqualTo(Instant.parse("2026-03-09T05:30:00Z"));
    }

    @Test
    void prefersEndsAtAndFallsBackToStartsAt() {
        Event event = configuredEvent("Asia/Yekaterinburg", 2);
        event.setStartsAt(Instant.parse("2026-01-01T00:00:00Z"));
        event.setEndsAt(Instant.parse("2026-01-03T12:00:00Z"));
        assertThat(service.calculate(event, Instant.parse("2026-01-04T00:00:00Z")).deadline())
                .isEqualTo(Instant.parse("2026-01-05T12:00:00Z"));

        event.setEndsAt(null);
        assertThat(service.calculate(event, Instant.parse("2026-01-01T01:00:00Z")).deadline())
                .isEqualTo(Instant.parse("2026-01-03T00:00:00Z"));
    }

    private static Event configuredEvent(String timeZone, int windowDays) {
        Event event = new Event();
        event.setTimeZone(timeZone);
        event.setResultInquiryEnabled(true);
        event.setResultInquiryWindowDays(windowDays);
        event.setResultInquiryEmail("timing@example.org");
        return event;
    }
}
