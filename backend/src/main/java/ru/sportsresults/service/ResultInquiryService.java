package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.ResultInquiryLookupDto;
import ru.sportsresults.domain.PublicResultVisibility;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultInquiryAvailability;
import ru.sportsresults.domain.ResultInquiryLookupState;
import ru.sportsresults.repository.ResultRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ResultInquiryService {

    private final ResultRepository resultRepository;
    private final ResultInquiryAvailabilityService availabilityService;
    private final PublicResultIdentityService identityService;

    public ResultInquiryService(
            ResultRepository resultRepository,
            ResultInquiryAvailabilityService availabilityService,
            PublicResultIdentityService identityService
    ) {
        this.resultRepository = resultRepository;
        this.availabilityService = availabilityService;
        this.identityService = identityService;
    }

    @Transactional(readOnly = true)
    public ResultInquiryLookupDto lookup(Long eventId, String bib) {
        return lookup(eventId, bib, null);
    }

    @Transactional(readOnly = true)
    public ResultInquiryLookupDto lookup(Long eventId, String bib, Long raceId) {
        PublicResultIdentityService.RegistrationBibLookup lookup = identityService.lookupByBib(eventId, bib);
        ResultInquiryAvailabilityDecision availability = availabilityService.calculate(lookup.event());
        List<Registration> candidates = identityService.publicCandidates(lookup, raceId);

        if (candidates.isEmpty()) {
            return safeState(ResultInquiryLookupState.NOT_FOUND, lookup.bib(), false, availability);
        }
        if (candidates.size() > 1) {
            return ambiguousState(
                    lookup.bib(), hasMissingResultCandidate(candidates), availability, candidates
            );
        }
        return resolveIdentifiedRegistration(candidates.getFirst(), lookup.bib(), availability);
    }

    private ResultInquiryLookupDto resolveIdentifiedRegistration(
            Registration registration,
            String bib,
            ResultInquiryAvailabilityDecision availability
    ) {
        if (!PublicResultVisibility.isRegistrationPublic(registration)) {
            return safeState(ResultInquiryLookupState.NOT_FOUND, bib, false, availability);
        }

        Result result = resultRepository.findByRegistrationId(registration.getId()).orElse(null);
        boolean resultPublic = result != null && PublicResultVisibility.isResultPublic(result);
        ResultInquiryLookupState state = resultPublic
                ? ResultInquiryLookupState.RESULT_PUBLIC
                : ResultInquiryLookupState.RESULT_NOT_PUBLIC;
        return new ResultInquiryLookupDto(
                state,
                availability.state(),
                registration.getBib(),
                resultPublic ? registration.getDisplayName() : null,
                registration.getRace().getId(),
                registration.getRace().getName(),
                registration.getRace().getName(),
                resultPublic ? result.getId() : null,
                !resultPublic,
                availability.deadline(),
                List.of()
        );
    }

    private ResultInquiryLookupDto ambiguousState(
            String bib,
            boolean missingResultActionAvailable,
            ResultInquiryAvailabilityDecision availability,
            List<Registration> registrations
    ) {
        Map<Long, ResultInquiryLookupDto.ResultInquiryCandidateDto> byRace = new LinkedHashMap<>();
        registrations.forEach(registration -> byRace.putIfAbsent(
                registration.getRace().getId(),
                new ResultInquiryLookupDto.ResultInquiryCandidateDto(
                        registration.getRace().getId(),
                        registration.getRace().getName(),
                        registration.getRace().getName()
                )
        ));
        return new ResultInquiryLookupDto(
                ResultInquiryLookupState.AMBIGUOUS,
                availability.state(),
                bib,
                null,
                null,
                null,
                null,
                null,
                missingResultActionAvailable,
                availability.deadline(),
                List.copyOf(byRace.values())
        );
    }

    private boolean hasMissingResultCandidate(List<Registration> registrations) {
        List<Registration> publicRegistrations = registrations.stream()
                .filter(PublicResultVisibility::isRegistrationPublic)
                .toList();
        if (publicRegistrations.isEmpty()) {
            return false;
        }
        Map<Long, Result> resultsByRegistration = resultRepository.findAllByRegistrationIdIn(
                        publicRegistrations.stream().map(Registration::getId).toList()
                ).stream()
                .collect(Collectors.toMap(result -> result.getRegistration().getId(), Function.identity()));
        return publicRegistrations.stream().anyMatch(registration -> {
            Result result = resultsByRegistration.get(registration.getId());
            return result == null || !PublicResultVisibility.isResultPublic(result);
        });
    }

    private static ResultInquiryLookupDto safeState(
            ResultInquiryLookupState state,
            String bib,
            boolean missingResultActionAvailable,
            ResultInquiryAvailabilityDecision availability
    ) {
        return new ResultInquiryLookupDto(
                state,
                availability.state(),
                bib,
                null,
                null,
                null,
                null,
                null,
                missingResultActionAvailable,
                availability.deadline(),
                List.of()
        );
    }
}
