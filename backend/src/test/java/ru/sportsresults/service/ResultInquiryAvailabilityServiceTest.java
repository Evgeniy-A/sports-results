package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.ResultInquiryAvailability;
import ru.sportsresults.domain.ResultInquiryDeadlineMode;

import java.time.Instant;
import java.time.LocalDate;

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
    void disabledStateKeepsTheConfiguredDeadlineVisibleToAdministrators() {
        Event event = configuredEvent("Europe/Moscow", 3);
        event.setResultInquiryEnabled(false);
        event.setEndsAt(Instant.parse("2026-01-10T10:00:00Z"));

        assertThat(service.calculate(event, Instant.parse("2026-01-11T00:00:00Z")))
                .isEqualTo(new ResultInquiryAvailabilityDecision(
                        ResultInquiryAvailability.DISABLED,
                        Instant.parse("2026-01-13T20:59:59.999999999Z")
                ));
    }

    @Test
    void distinguishesNotOpenOpenAndClosed() {
        Event event = configuredEvent("Europe/Moscow", 5);
        event.setEndsAt(Instant.parse("2026-01-10T10:00:00Z"));

        assertThat(service.calculate(event, Instant.parse("2026-01-10T09:59:59Z")).state())
                .isEqualTo(ResultInquiryAvailability.NOT_OPEN_YET);
        assertThat(service.calculate(event, Instant.parse("2026-01-10T10:00:00Z")).state())
                .isEqualTo(ResultInquiryAvailability.OPEN);
        assertThat(service.calculate(event, Instant.parse("2026-01-15T20:59:59Z")).state())
                .isEqualTo(ResultInquiryAvailability.OPEN);
        assertThat(service.calculate(event, Instant.parse("2026-01-15T20:59:59.999999999Z")).state())
                .isEqualTo(ResultInquiryAvailability.OPEN);
        assertThat(service.calculate(event, Instant.parse("2026-01-15T21:00:00Z")).state())
                .isEqualTo(ResultInquiryAvailability.CLOSED);
    }

    @Test
    void addsCalendarDaysInTheEventTimeZoneAcrossDst() {
        Event event = configuredEvent("America/New_York", 1);
        event.setEndsAt(Instant.parse("2026-03-08T06:30:00Z"));

        assertThat(service.calculate(event, Instant.parse("2026-03-08T07:00:00Z")).deadline())
                .isEqualTo(Instant.parse("2026-03-10T03:59:59.999999999Z"));
    }

    @Test
    void prefersEndsAtAndFallsBackToStartsAt() {
        Event event = configuredEvent("Asia/Yekaterinburg", 2);
        event.setStartsAt(Instant.parse("2026-01-01T00:00:00Z"));
        event.setEndsAt(Instant.parse("2026-01-03T12:00:00Z"));
        assertThat(service.calculate(event, Instant.parse("2026-01-04T00:00:00Z")).deadline())
                .isEqualTo(Instant.parse("2026-01-05T18:59:59.999999999Z"));

        event.setEndsAt(null);
        assertThat(service.calculate(event, Instant.parse("2026-01-01T01:00:00Z")).deadline())
                .isEqualTo(Instant.parse("2026-01-03T18:59:59.999999999Z"));
    }

    @Test
    void fixedDateUsesTheWholeSelectedLocalCalendarDay() {
        Event event = configuredEvent("Asia/Yekaterinburg", 3);
        event.setResultInquiryDeadlineMode(ResultInquiryDeadlineMode.FIXED_DATE);
        event.setResultInquiryFixedDate(LocalDate.of(2027, 8, 20));
        event.setEndsAt(Instant.parse("2027-08-15T13:00:00Z"));

        assertThat(service.calculate(event, Instant.parse("2027-08-20T18:59:59Z")).state())
                .isEqualTo(ResultInquiryAvailability.OPEN);
        assertThat(service.calculate(event, Instant.parse("2027-08-20T18:59:59.999999999Z")).deadline())
                .isEqualTo(Instant.parse("2027-08-20T18:59:59.999999999Z"));
        assertThat(service.calculate(event, Instant.parse("2027-08-20T19:00:00Z")).state())
                .isEqualTo(ResultInquiryAvailability.CLOSED);
    }

    @Test
    void modeSwitchUsesOnlyTheActiveConfigurationField() {
        Event event = configuredEvent("Asia/Yekaterinburg", 5);
        event.setEndsAt(Instant.parse("2027-08-15T13:00:00Z"));
        event.setResultInquiryFixedDate(LocalDate.of(2027, 8, 25));

        assertThat(service.calculate(event, Instant.parse("2027-08-20T00:00:00Z")).deadline())
                .isEqualTo(Instant.parse("2027-08-20T18:59:59.999999999Z"));

        event.setResultInquiryDeadlineMode(ResultInquiryDeadlineMode.FIXED_DATE);
        assertThat(service.calculate(event, Instant.parse("2027-08-20T00:00:00Z")).deadline())
                .isEqualTo(Instant.parse("2027-08-25T18:59:59.999999999Z"));
    }

    @Test
    void expiredFixedDateIsClosedEvenBeforeEventDatesAreConfigured() {
        Event event = configuredEvent("Europe/Moscow", 3);
        event.setResultInquiryDeadlineMode(ResultInquiryDeadlineMode.FIXED_DATE);
        event.setResultInquiryFixedDate(LocalDate.of(2026, 1, 10));

        assertThat(service.calculate(event, Instant.parse("2026-01-10T21:00:00Z")).state())
                .isEqualTo(ResultInquiryAvailability.CLOSED);
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
