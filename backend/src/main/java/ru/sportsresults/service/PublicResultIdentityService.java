package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.PublicResultVisibility;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultsPublicationStatus;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.RegistrationRepository;
import ru.sportsresults.repository.ResultRepository;

import java.util.List;
import java.util.Optional;

@Service
public class PublicResultIdentityService {

    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final ResultRepository resultRepository;

    public PublicResultIdentityService(
            EventRepository eventRepository,
            RegistrationRepository registrationRepository,
            ResultRepository resultRepository
    ) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.resultRepository = resultRepository;
    }

    @Transactional(readOnly = true)
    public RegistrationBibLookup lookupByBib(Long eventId, String bib) {
        Event event = requirePublicEvent(eventId);
        String exactBib = normalizeBib(bib);
        return new RegistrationBibLookup(
                event,
                exactBib,
                registrationRepository.findAllByRaceEventIdAndBib(eventId, exactBib).stream()
                        // Draft races must not disclose hidden duplicate-bib candidates. The
                        // public candidate filter is applied before any lookup context is returned.
                        .filter(registration -> registration.getRace().getResultsPublicationStatus()
                                == ResultsPublicationStatus.PUBLISHED)
                        .toList()
        );
    }

    public List<Registration> publicCandidates(RegistrationBibLookup lookup, Long raceId) {
        return lookup.registrations().stream()
                .filter(PublicResultVisibility::isRegistrationPublic)
                .filter(registration -> raceId == null || registration.getRace().getId().equals(raceId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Result> findPublicResult(Event event, Long resultId) {
        if (resultId == null) {
            return Optional.empty();
        }
        return resultRepository.findById(resultId)
                .filter(result -> result.getRegistration().getRace().getEvent().getId().equals(event.getId()))
                .filter(PublicResultVisibility::isResultPublic);
    }

    @Transactional(readOnly = true)
    public Event requirePublicEvent(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EVENT_NOT_FOUND", "Published event not found"
                ));
        if (!PublicResultVisibility.isEventPublic(event)) {
            throw new ResourceNotFoundException("EVENT_NOT_FOUND", "Published event not found");
        }
        return event;
    }

    private static String normalizeBib(String bib) {
        if (bib == null || bib.isBlank()) {
            throw new InvalidRequestException("BIB_REQUIRED", "bib is required");
        }
        String normalized = bib.strip();
        if (normalized.length() > 64) {
            throw new InvalidRequestException("INVALID_BIB", "bib must not exceed 64 characters");
        }
        return normalized;
    }

    public record RegistrationBibLookup(
            Event event,
            String bib,
            List<Registration> registrations
    ) {
        public RegistrationBibLookup {
            registrations = List.copyOf(registrations);
        }
    }
}
