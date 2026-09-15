package ru.sportsresults.service;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.AdminResultIssueAttachmentDownloadDto;
import ru.sportsresults.api.dto.AdminResultIssueAttachmentDto;
import ru.sportsresults.api.dto.AdminResultIssueArchiveDto;
import ru.sportsresults.api.dto.AdminResultIssueDetailDto;
import ru.sportsresults.api.dto.AdminResultIssueListItemDto;
import ru.sportsresults.api.dto.AdminResultIssueRaceSummaryDto;
import ru.sportsresults.api.dto.AdminResultIssueRegistrationDto;
import ru.sportsresults.api.dto.AdminResultIssueRegistrationSummaryDto;
import ru.sportsresults.api.dto.AdminResultIssueResultDto;
import ru.sportsresults.api.dto.AdminResultIssueStatusDto;
import ru.sportsresults.api.dto.AdminResultIssueSnapshotDto;
import ru.sportsresults.api.dto.CategoryDto;
import ru.sportsresults.api.dto.PageResponse;
import ru.sportsresults.api.dto.RankingAchievementDto;
import ru.sportsresults.api.dto.UpdateResultIssueStatusRequest;
import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueAttachment;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.domain.ResultIssueQueueScope;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;
import ru.sportsresults.repository.AdminResultIssueListProjection;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.ResultIssueAttachmentRepository;
import ru.sportsresults.repository.ResultIssueRequestRepository;
import ru.sportsresults.repository.ResultIssueRequestSearchCriteria;
import ru.sportsresults.storage.attachments.PreparedObjectDownload;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.ObjectMapper;

@Service
public class AdminResultIssueService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    private final EventRepository eventRepository;
    private final ResultIssueRequestRepository issueRepository;
    private final ResultIssueAttachmentRepository attachmentRepository;
    private final ResultIssueAttachmentService attachmentService;
    private final ResultIssueHistoryService historyService;
    private final ObjectMapper objectMapper;
    private final EventResultDataMutationGuard mutationGuard;

    public AdminResultIssueService(
            EventRepository eventRepository,
            ResultIssueRequestRepository issueRepository,
            ResultIssueAttachmentRepository attachmentRepository,
            ResultIssueAttachmentService attachmentService,
            ResultIssueHistoryService historyService,
            ObjectMapper objectMapper,
            EventResultDataMutationGuard mutationGuard
    ) {
        this.eventRepository = eventRepository;
        this.issueRepository = issueRepository;
        this.attachmentRepository = attachmentRepository;
        this.attachmentService = attachmentService;
        this.historyService = historyService;
        this.objectMapper = objectMapper;
        this.mutationGuard = mutationGuard;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminResultIssueListItemDto> search(
            Long eventId,
            ResultIssueStatus status,
            ResultIssueType issueType,
            Long issueIdFrom,
            Long issueIdTo,
            String bib,
            ResultCorrectionReason correctionReason,
            ResultIssueQueueScope queueScope,
            int page,
            int size
    ) {
        requireEvent(eventId);
        validatePage(page, size);
        validateRange(issueIdFrom, issueIdTo);
        Page<AdminResultIssueListProjection> result = issueRepository.searchAdmin(
                new ResultIssueRequestSearchCriteria(
                        eventId,
                        status,
                        issueType,
                        issueIdFrom,
                        issueIdTo,
                        normalize(bib),
                        correctionReason,
                        queueScope
                ),
                page,
                size
        );
        return new PageResponse<>(
                result.getContent().stream().map(AdminResultIssueService::toListItem).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages(),
                "issueId",
                "asc"
        );
    }

    @Transactional(readOnly = true)
    public AdminResultIssueDetailDto get(Long eventId, Long issueId) {
        ResultIssueRequest issue = requireIssue(eventId, issueId);
        List<ResultIssueAttachment> attachments =
                attachmentRepository.findAllByIssueRequest_IdOrderByCreatedAtAscIdAsc(issueId);
        return toDetail(issue, attachments);
    }

    @Transactional
    public AdminResultIssueStatusDto updateStatus(
            Long eventId,
            Long issueId,
            UpdateResultIssueStatusRequest request,
            String actor
    ) {
        mutationGuard.lock(eventId);
        if (request.expectedStatus() == request.status()) {
            ResultIssueRequest unchanged = requireIssue(eventId, issueId);
            if (unchanged.getStatus() != request.expectedStatus()) {
                throw alreadyChanged();
            }
            return statusDto(unchanged);
        }
        validateTransition(request.expectedStatus(), request.status());
        Instant now = Instant.now();
        Instant resolvedAt = isTerminal(request.status()) ? now : null;
        int updated = issueRepository.updateStatusIfCurrent(
                eventId,
                issueId,
                request.expectedStatus().name(),
                request.status().name(),
                resolvedAt,
                now
        );
        if (updated == 0) {
            if (!issueRepository.existsByIdAndEvent_Id(issueId, eventId)) {
                throw notFound();
            }
            throw alreadyChanged();
        }
        ResultIssueRequest issue = requireIssue(eventId, issueId);
        historyService.statusChanged(
                issue, request.expectedStatus(), request.status(), actor, now
        );
        return statusDto(issue);
    }

    @Transactional(readOnly = true)
    public AdminResultIssueAttachmentDownloadDto prepareAttachmentDownload(
            Long eventId,
            Long issueId,
            Long attachmentId
    ) {
        requireIssue(eventId, issueId);
        ResultIssueAttachment attachment = attachmentRepository.findByIdAndIssueRequest_Id(attachmentId, issueId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RESULT_ISSUE_ATTACHMENT_NOT_FOUND", "Attachment not found in this result issue request"
                ));
        if (attachment.getUploadStatus() != AttachmentUploadStatus.UPLOADED
                || attachment.getScanStatus() != AttachmentScanStatus.CLEAN) {
            throw new RequestConflictException(
                    "RESULT_ISSUE_ATTACHMENT_NOT_AVAILABLE",
                    "Attachment is not available for download"
            );
        }
        PreparedObjectDownload download = attachmentService.prepareAuthorizedDownload(attachmentId);
        return new AdminResultIssueAttachmentDownloadDto(download.url().toString(), download.expiresAt());
    }

    private AdminResultIssueDetailDto toDetail(
            ResultIssueRequest issue,
            List<ResultIssueAttachment> attachments
    ) {
        Registration registration = issue.getRegistration();
        Race race = registration.getRace();
        Category category = registration.getCategory();
        Result result = issue.getResult();
        return new AdminResultIssueDetailDto(
                issue.getId(),
                issue.getEvent().getId(),
                issue.getIssueType(),
                issue.getCorrectionReason(),
                issue.getStatus(),
                issue.getContactEmail(),
                issue.getMessage(),
                issue.getCreatedAt(),
                issue.getUpdatedAt(),
                issue.getResolvedAt(),
                milliseconds(issue.getClaimedGunTime()),
                milliseconds(issue.getClaimedChipTime()),
                issue.getEstimatedStartAt(),
                issue.getEstimatedFinishAt(),
                milliseconds(issue.getObservedGunTime()),
                milliseconds(issue.getObservedChipTime()),
                issue.getObservedResultStatus(),
                new AdminResultIssueArchiveDto(
                        issue.getId(),
                        issue.getStatus(),
                        issue.getQueueArchivedAt(),
                        issue.getQueueArchivedBy(),
                        issue.getQueueArchiveReason(),
                        issue.getQueueArchivedImportOperationId()
                ),
                new AdminResultIssueSnapshotDto(
                        issue.getSnapshotOrigin(),
                        issue.getSnapshotEventName(),
                        issue.getSnapshotEventLocation(),
                        issue.getSnapshotEventStartsAt(),
                        issue.getSnapshotSportFormatId(),
                        issue.getSnapshotSportFormatName(),
                        issue.getSnapshotSportFormatCode(),
                        issue.getSnapshotRaceId(),
                        issue.getSnapshotRaceName(),
                        issue.getSnapshotRaceCode(),
                        issue.getSnapshotRaceDistanceMeters(),
                        issue.getSnapshotBib(),
                        issue.getSnapshotDisplayName(),
                        issue.getSnapshotEffectiveCategoryName(),
                        issue.getSnapshotSourceCategory(),
                        issue.getSnapshotCategoryPubliclyEnabled(),
                        milliseconds(issue.getObservedGunTime()),
                        milliseconds(issue.getObservedChipTime()),
                        issue.getObservedResultStatus(),
                        readRanking(issue.getSnapshotRanking()),
                        issue.getSnapshotImportBatchId(),
                        issue.getSnapshotSourceRowNumber()
                ),
                new AdminResultIssueRegistrationDto(
                        registration.getId(),
                        registration.getBib(),
                        registration.getDisplayName(),
                        registration.getFirstName(),
                        registration.getLastName(),
                        registration.getBirthDate(),
                        registration.getGender(),
                        registration.getSourceCategory(),
                        registration.getEntryKind(),
                        category == null ? null : new CategoryDto(category.getId(), category.getDisplayName()),
                        race.getId(),
                        race.getName()
                ),
                result == null ? null : new AdminResultIssueResultDto(
                        result.getId(),
                        result.getStatus(),
                        milliseconds(result.getGunTime()),
                        milliseconds(result.getChipTime()),
                        result.getOverallPlace(),
                        result.getGenderPlace(),
                        result.getCategoryPlace(),
                        result.getNetOverallPlace(),
                        result.getNetGenderPlace(),
                        result.getNetCategoryPlace(),
                        List.of()
                ),
                attachments.stream().map(AdminResultIssueService::toAttachment).toList()
        );
    }

    private ResultIssueRequest requireIssue(Long eventId, Long issueId) {
        return issueRepository.findByIdAndEvent_Id(issueId, eventId).orElseThrow(AdminResultIssueService::notFound);
    }

    private void requireEvent(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found");
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("INVALID_PAGE", "page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "INVALID_PAGE_SIZE", "size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
    }

    private static void validateRange(Long issueIdFrom, Long issueIdTo) {
        if ((issueIdFrom != null && issueIdFrom < 1) || (issueIdTo != null && issueIdTo < 1)) {
            throw new InvalidRequestException("INVALID_ISSUE_ID_RANGE", "issueId bounds must be positive");
        }
        if (issueIdFrom != null && issueIdTo != null && issueIdFrom > issueIdTo) {
            throw new InvalidRequestException(
                    "INVALID_ISSUE_ID_RANGE", "issueIdFrom must not be greater than issueIdTo"
            );
        }
    }

    private static void validateTransition(ResultIssueStatus expected, ResultIssueStatus next) {
        boolean allowed = (expected == ResultIssueStatus.NEW
                && (next == ResultIssueStatus.IN_PROGRESS || isTerminal(next)))
                || (expected == ResultIssueStatus.IN_PROGRESS && isTerminal(next));
        if (!allowed) {
            throw new RequestConflictException(
                    "INVALID_RESULT_ISSUE_STATUS_TRANSITION",
                    "This result issue status transition is not allowed"
            );
        }
    }

    private static boolean isTerminal(ResultIssueStatus status) {
        return status == ResultIssueStatus.RESOLVED || status == ResultIssueStatus.REJECTED;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Long milliseconds(Duration duration) {
        return duration == null ? null : duration.toMillis();
    }

    private static AdminResultIssueListItemDto toListItem(AdminResultIssueListProjection item) {
        return new AdminResultIssueListItemDto(
                item.issueId(),
                item.status(),
                item.issueType(),
                item.correctionReason(),
                item.createdAt(),
                item.queueArchivedAt(),
                new AdminResultIssueRegistrationSummaryDto(
                        item.registrationId(), item.bib(), item.displayName()
                ),
                new AdminResultIssueRaceSummaryDto(
                        item.raceId(), item.raceName()
                ),
                item.resultId(),
                item.attachmentCount()
        );
    }

    private static AdminResultIssueAttachmentDto toAttachment(ResultIssueAttachment attachment) {
        return new AdminResultIssueAttachmentDto(
                attachment.getId(),
                attachment.getOriginalFileName(),
                attachment.getContentType(),
                attachment.getDetectedContentType(),
                attachment.getSizeBytes(),
                attachment.getUploadStatus(),
                attachment.getScanStatus(),
                attachment.getCreatedAt(),
                attachment.getUploadedAt(),
                attachment.getScannedAt(),
                attachment.getDeletedAt()
        );
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException(
                "RESULT_ISSUE_NOT_FOUND", "Result issue request not found in this event"
        );
    }

    private List<RankingAchievementDto> readRanking(String ranking) {
        if (ranking == null) {
            return null;
        }
        try {
            return List.of(objectMapper.readValue(ranking, RankingAchievementDto[].class));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read result issue ranking snapshot", exception);
        }
    }

    private static AdminResultIssueStatusDto statusDto(ResultIssueRequest issue) {
        return new AdminResultIssueStatusDto(
                issue.getId(), issue.getStatus(), issue.getUpdatedAt(), issue.getResolvedAt()
        );
    }

    private static RequestConflictException alreadyChanged() {
        return new RequestConflictException(
                "RESULT_ISSUE_ALREADY_CHANGED",
                "The result issue request was already changed by another administrator; refresh it"
        );
    }
}
