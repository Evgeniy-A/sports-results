package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.EventPhase;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EventPhaseCalculatorTest {

    @Test
    void derivesUpcomingOngoingAndPastUsingEventLocalDates() {
        Event event = event("2027-06-15T21:30:00Z", null, "Europe/Moscow");

        assertThat(EventPhaseCalculator.calculate(event, Instant.parse("2027-06-14T20:00:00Z")))
                .isEqualTo(EventPhase.UPCOMING);
        assertThat(EventPhaseCalculator.calculate(event, Instant.parse("2027-06-15T21:40:00Z")))
                .isEqualTo(EventPhase.ONGOING);
        assertThat(EventPhaseCalculator.calculate(event, Instant.parse("2027-06-16T21:01:00Z")))
                .isEqualTo(EventPhase.PAST);
    }

    @Test
    void treatsEveryLocalDayOfMultiDayEventAsOngoing() {
        Event event = event("2027-06-14T21:00:00Z", "2027-06-17T20:59:00Z", "Europe/Moscow");

        assertThat(EventPhaseCalculator.calculate(event, Instant.parse("2027-06-16T10:00:00Z")))
                .isEqualTo(EventPhase.ONGOING);
    }

    private static Event event(String start, String end, String zone) {
        Event event = new Event();
        event.setStartsAt(Instant.parse(start));
        event.setEndsAt(end == null ? null : Instant.parse(end));
        event.setTimeZone(zone);
        return event;
    }
}
