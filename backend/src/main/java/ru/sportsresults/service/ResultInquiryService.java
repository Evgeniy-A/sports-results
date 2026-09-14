package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.ActiveResultIssueDto;
import ru.sportsresults.api.dto.ResultInquiryLookupDto;
import ru.sportsresults.domain.PublicResultVisibility;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultInquiryAvailability;
import ru.sportsresults.domain.ResultInquiryLookupState;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.repository.ResultIssueRequestRepository;
import ru.sportsresults.repository.ResultRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ResultInquiryService {

    private final ResultRepository resultRepository;
    private final ResultIssueRequestRepository issueRepository;
    private final ResultInquiryAvailabilityService availabilityService;
    private final PublicResultIdentityService identityService;

    public ResultInquiryService(
            ResultRepository resultRepository,
            ResultIssueRequestRepository issueRepository,
            ResultInquiryAvailabilityService availabilityService,
            PublicResultIdentityService identityService
    ) {
        this.resultRepository = resultRepository;
        this.issueRepository = issueRepository;
        this.availabilityService = availabilityService;
        this.identityService = identityService;
    }

    @Transactional(readOnly = true)
    public ResultInquiryLookupDto lookup(Long eventId, String bib) {
        PublicResultIdentityService.RegistrationBibLookup lookup = identityService.lookupByBib(eventId, bib);
        ResultInquiryAvailabilityDecision availability = availabilityService.calculate(lookup.event());

        if (lookup.registrations().isEmpty()) {
            return safeState(ResultInquiryLookupState.NOT_FOUND, lookup.bib(), false, availability);
        }
        if (lookup.registrations().size() > 1) {
            return safeState(
                    ResultInquiryLookupState.NEEDS_VERIFICATION,
                    lookup.bib(),
                    hasMissingResultCandidate(lookup.registrations()),
                    availability
            );
        }
        return resolveIdentifiedRegistration(
                lookup.registrations().getFirst(), lookup.bib(), availability,
                ResultInquiryLookupState.NOT_FOUND, null
        );
    }

    @Transactional(readOnly = true)
    public ResultInquiryLookupDto verify(Long eventId, String bib, LocalDate birthDate) {
        PublicResultIdentityService.RegistrationBibLookup lookup = identityService.lookupByBib(eventId, bib);
        ResultInquiryAvailabilityDecision availability = availabilityService.calculate(lookup.event());
        Registration registration = identityService.verifyByBirthDate(lookup, birthDate).orElse(null);
        if (registration == null) {
            return safeState(
                    ResultInquiryLookupState.VERIFICATION_FAILED,
                    lookup.bib(),
                    hasMissingResultCandidate(lookup.registrations()),
                    availability
            );
        }
        return resolveIdentifiedRegistration(
                registration, lookup.bib(), availability, ResultInquiryLookupState.VERIFICATION_FAILED,
                activeIssue(registration)
        );
    }

    private ResultInquiryLookupDto resolveIdentifiedRegistration(
            Registration registration,
            String bib,
            ResultInquiryAvailabilityDecision availability,
            ResultInquiryLookupState invisibleState,
            ActiveResultIssueDto activeIssue
    ) {
        if (!PublicResultVisibility.isRegistrationPublic(registration)) {
            return safeState(invisibleState, bib, false, availability);
        }

        Result result = resultRepository.findByRegistrationId(registration.getId()).orElse(null);
        boolean resultPublic = result != null && PublicResultVisibility.isResultPublic(result);
        ResultInquiryLookupState state = resultPublic
                ? ResultInquiryLookupState.RESULT_PUBLIC
                : ResultInquiryLookupState.RESULT_NOT_PUBLIC;
        String contactEmail = state == ResultInquiryLookupState.RESULT_NOT_PUBLIC
                && availability.state() == ResultInquiryAvailability.OPEN
                ? registration.getRace().getEvent().getResultInquiryEmail()
                : null;

        return new ResultInquiryLookupDto(
                state,
                availability.state(),
                registration.getBib(),
                registration.getDisplayName(),
                registration.getRace().getId(),
                registration.getRace().getName(),
                registration.getRace().getSportFormat().getDisplayName(),
                resultPublic ? result.getId() : null,
                !resultPublic,
                availability.deadline(),
                contactEmail,
                activeIssue
        );
    }

    private ActiveResultIssueDto activeIssue(Registration registration) {
        return issueRepository.findFirstByRegistration_IdAndQueueArchivedAtIsNullAndStatusInOrderByIdAsc(
                        registration.getId(), ResultIssueStatus.activeStatuses()
                )
                .map(issue -> new ActiveResultIssueDto(issue.getId()))
                .orElse(null);
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
                null,
                null
        );
    }
}
