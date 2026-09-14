package ru.sportsresults.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.CreateMissingResultIssueRequest;
import ru.sportsresults.api.dto.CreateResultCorrectionIssueRequest;
import ru.sportsresults.api.dto.ResultIssueCreatedDto;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.PublicResultVisibility;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;
import ru.sportsresults.repository.ResultIssueRequestRepository;
import ru.sportsresults.repository.ResultRepository;

import java.time.Duration;

@Service
public class ResultIssueRequestService {

    private static final String PUBLIC_ACTOR = "PUBLIC_API";

    private final ResultIssueRequestRepository issueRepository;
    private final ResultRepository resultRepository;
    private final ResultInquiryAvailabilityService availabilityService;
    private final PublicResultIdentityService identityService;
    private final ResultIssueAttachmentCapabilityService attachmentCapabilityService;
    private final ResultIssueSnapshotService snapshotService;
    private final ResultIssueHistoryService historyService;
    private final EventResultDataMutationGuard mutationGuard;

    public ResultIssueRequestService(
            ResultIssueRequestRepository issueRepository,
            ResultRepository resultRepository,
            ResultInquiryAvailabilityService availabilityService,
            PublicResultIdentityService identityService,
            ResultIssueAttachmentCapabilityService attachmentCapabilityService,
            ResultIssueSnapshotService snapshotService,
            ResultIssueHistoryService historyService,
            EventResultDataMutationGuard mutationGuard
    ) {
        this.issueRepository = issueRepository;
        this.resultRepository = resultRepository;
        this.availabilityService = availabilityService;
        this.identityService = identityService;
        this.attachmentCapabilityService = attachmentCapabilityService;
        this.snapshotService = snapshotService;
        this.historyService = historyService;
        this.mutationGuard = mutationGuard;
    }

    @Transactional
    public ResultIssueCreatedDto createMissingResult(
            Long eventId,
            CreateMissingResultIssueRequest request
    ) {
        mutationGuard.lock(eventId);
        PublicResultIdentityService.RegistrationBibLookup lookup = identityService.lookupByBib(eventId, request.bib());
        requireOpen(lookup.event());
        Registration registration = identityService.verifyByBirthDate(lookup, request.birthDate())
                .orElseThrow(ResultIssueRequestService::verificationFailed);
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElse(null);
        if (result != null && PublicResultVisibility.isResultPublic(result)) {
            throw new RequestConflictException(
                    "PUBLIC_RESULT_EXISTS",
                    "A public result already exists; use a result correction request"
            );
        }
        validateEstimatedTimes(request.estimatedStartAt(), request.estimatedFinishAt());

        ResultIssueRequest issue = baseIssue(
                lookup.event(), registration, ResultIssueType.MISSING_RESULT,
                request.contactEmail(), request.message()
        );
        issue.setResult(result);
        issue.setEstimatedStartAt(request.estimatedStartAt());
        issue.setEstimatedFinishAt(request.estimatedFinishAt());
        return saveNew(issue);
    }

    @Transactional
    public ResultIssueCreatedDto createResultCorrection(
            Long eventId,
            Long resultId,
            CreateResultCorrectionIssueRequest request
    ) {
        mutationGuard.lock(eventId);
        Event event = identityService.requirePublicEvent(eventId);
        requireOpen(event);
        Result result = identityService.verifyPublicResult(event, resultId, request.birthDate())
                .orElseThrow(ResultIssueRequestService::verificationFailed);

        ResultIssueRequest issue = baseIssue(
                event, result.getRegistration(), ResultIssueType.RESULT_CORRECTION,
                request.contactEmail(), request.message()
        );
        issue.setResult(result);
        issue.setCorrectionReason(request.correctionReason());
        issue.setClaimedGunTime(duration(request.claimedGunTimeMs()));
        issue.setClaimedChipTime(duration(request.claimedChipTimeMs()));
        issue.setObservedGunTime(result.getGunTime());
        issue.setObservedChipTime(result.getChipTime());
        issue.setObservedResultStatus(result.getStatus());
        return saveNew(issue);
    }

    private ResultIssueCreatedDto saveNew(ResultIssueRequest issue) {
        if (findActive(issue.getRegistration().getId()).isPresent()) {
            throw activeIssueExists();
        }
        try {
            snapshotService.capture(issue);
            ResultIssueAttachmentCapabilityService.IssuedCapability capability =
                    attachmentCapabilityService.issueFor(issue);
            ResultIssueRequest saved = issueRepository.saveAndFlush(issue);
            historyService.created(saved, PUBLIC_ACTOR, saved.getCreatedAt());
            return new ResultIssueCreatedDto(
                    saved.getId(), saved.getIssueType(), saved.getStatus(), saved.getCreatedAt(),
                    capability.token(), capability.expiresAt()
            );
        } catch (DataIntegrityViolationException exception) {
            throw activeIssueExists();
        }
    }

    private java.util.Optional<ResultIssueRequest> findActive(Long registrationId) {
        return issueRepository.findFirstByRegistration_IdAndQueueArchivedAtIsNullAndStatusInOrderByIdAsc(
                registrationId, ResultIssueStatus.activeStatuses()
        );
    }

    private static ResultIssueRequest baseIssue(
            Event event,
            Registration registration,
            ResultIssueType type,
            String contactEmail,
            String message
    ) {
        ResultIssueRequest issue = new ResultIssueRequest();
        issue.setEvent(event);
        issue.setRegistration(registration);
        issue.setIssueType(type);
        issue.setStatus(ResultIssueStatus.NEW);
        issue.setContactEmail(contactEmail.strip());
        issue.setMessage(message.strip());
        return issue;
    }

    private void requireOpen(Event event) {
        ResultInquiryAvailabilityDecision availability = availabilityService.calculate(event);
        switch (availability.state()) {
            case OPEN -> { }
            case DISABLED -> throw new RequestConflictException(
                    "RESULT_ISSUES_DISABLED", "Result issue requests are disabled for this event");
            case NOT_OPEN_YET -> throw new RequestConflictException(
                    "RESULT_ISSUES_NOT_OPEN", "Result issue requests are not open yet");
            case CLOSED -> throw new RequestConflictException(
                    "RESULT_ISSUES_CLOSED", "The result issue request window is closed");
        }
    }

    private static void validateEstimatedTimes(java.time.Instant start, java.time.Instant finish) {
        if (start != null && finish != null && finish.isBefore(start)) {
            throw new InvalidRequestException(
                    "INVALID_ESTIMATED_TIMES", "estimatedFinishAt must not be before estimatedStartAt"
            );
        }
    }

    private static Duration duration(Long milliseconds) {
        return milliseconds == null ? null : Duration.ofMillis(milliseconds);
    }

    private static InvalidRequestException verificationFailed() {
        return new InvalidRequestException(
                "IDENTITY_VERIFICATION_FAILED",
                "Participant data could not be verified"
        );
    }

    private static RequestConflictException activeIssueExists() {
        return new RequestConflictException(
                "ACTIVE_RESULT_ISSUE_EXISTS",
                "An active result issue request already exists; another request is not required"
        );
    }
}
