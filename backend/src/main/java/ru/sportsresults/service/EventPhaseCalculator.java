package ru.sportsresults.service;

import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.EventPhase;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class EventPhaseCalculator {

    private EventPhaseCalculator() {
    }

    public static EventPhase calculate(Event event, Instant now) {
        if (event.getStartsAt() == null) {
            return EventPhase.UPCOMING;
        }
        ZoneId zone = ZoneId.of(event.getTimeZone());
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate start = event.getStartsAt().atZone(zone).toLocalDate();
        LocalDate end = (event.getEndsAt() == null ? event.getStartsAt() : event.getEndsAt())
                .atZone(zone)
                .toLocalDate();
        if (today.isBefore(start)) {
            return EventPhase.UPCOMING;
        }
        return today.isAfter(end) ? EventPhase.PAST : EventPhase.ONGOING;
    }
}
