package ru.sportsresults.domain;

import java.util.Set;

public final class PublicResultVisibility {

    private static final Set<String> PUBLIC_STATUSES = Set.of("finished", "disqualified");

    private PublicResultVisibility() {
    }

    public static Set<String> publicStatuses() {
        return PUBLIC_STATUSES;
    }

    public static boolean isPublicStatus(String status) {
        return status != null && PUBLIC_STATUSES.contains(status.toLowerCase(java.util.Locale.ROOT));
    }

    public static boolean isEventPublic(Event event) {
        return event.getPublicationStatus() == EventPublicationStatus.PUBLISHED
                && event.getResultsPublicationStatus() == ResultsPublicationStatus.PUBLISHED
                && event.getEventSeries().isActive();
    }

    public static boolean isRegistrationPublic(Registration registration) {
        return registration.isCurrent() && isRaceResultsPublic(registration.getRace());
    }

    public static boolean isRaceResultsPublic(Race race) {
        return isEventPublic(race.getEvent())
                && race.isPublicVisible()
                && race.getResultsPublicationStatus() == ResultsPublicationStatus.PUBLISHED;
    }

    public static boolean isResultPublic(Result result) {
        return isRegistrationPublic(result.getRegistration()) && isPublicStatus(result.getStatus());
    }
}
