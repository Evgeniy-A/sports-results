package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.domain.ResultsPublicationStatus;
import ru.sportsresults.repository.RaceRepository;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
public class EventResultsPublicationStateResolver {

    private static final State DRAFT = new State(ResultsPublicationStatus.DRAFT, false);
    private static final State PUBLISHED = new State(ResultsPublicationStatus.PUBLISHED, true);

    private final RaceRepository raceRepository;

    public EventResultsPublicationStateResolver(RaceRepository raceRepository) {
        this.raceRepository = raceRepository;
    }

    public State resolve(Long eventId) {
        boolean published = raceRepository.existsByEventIdAndPublicVisibleTrueAndResultsPublicationStatus(
                eventId, ResultsPublicationStatus.PUBLISHED
        );
        return published ? PUBLISHED : DRAFT;
    }

    public Map<Long, State> resolveAll(Collection<Long> eventIds) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> publishedEventIds = new HashSet<>(
                raceRepository.findEventIdsWithPublicVisibleResultsStatus(
                        eventIds, ResultsPublicationStatus.PUBLISHED
                )
        );
        Map<Long, State> states = new HashMap<>();
        eventIds.forEach(eventId -> states.put(eventId, publishedEventIds.contains(eventId) ? PUBLISHED : DRAFT));
        return states;
    }

    public record State(ResultsPublicationStatus status, boolean published) {
    }
}
