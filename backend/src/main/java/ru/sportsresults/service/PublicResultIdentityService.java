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

import java.time.LocalDate;
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
                        // existing visibility check is still applied after DOB verification.
                        .filter(registration -> registration.getRace().getResultsPublicationStatus()
                                == ResultsPublicationStatus.PUBLISHED)
                        .toList()
        );
    }

    public Optional<Registration> verifyByBirthDate(
            RegistrationBibLookup lookup,
            LocalDate birthDate
    ) {
        requireBirthDate(birthDate);
        List<Registration> matches = lookup.registrations().stream()
                .filter(registration -> birthDate.equals(registration.getBirthDate()))
                .toList();
        if (matches.size() != 1 || !PublicResultVisibility.isRegistrationPublic(matches.getFirst())) {
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }

    @Transactional(readOnly = true)
    public Optional<Result> verifyPublicResult(Event event, Long resultId, LocalDate birthDate) {
        requireBirthDate(birthDate);
        if (resultId == null) {
            return Optional.empty();
        }
        return resultRepository.findById(resultId)
                .filter(result -> result.getRegistration().getRace().getEvent().getId().equals(event.getId()))
                .filter(PublicResultVisibility::isResultPublic)
                .filter(result -> birthDate.equals(result.getRegistration().getBirthDate()));
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

    private static void requireBirthDate(LocalDate birthDate) {
        if (birthDate == null) {
            throw new InvalidRequestException("BIRTH_DATE_REQUIRED", "birthDate is required");
        }
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
