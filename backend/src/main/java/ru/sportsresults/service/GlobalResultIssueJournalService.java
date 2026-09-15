package ru.sportsresults.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.AdminResultIssueAttachmentDto;
import ru.sportsresults.api.dto.AdminResultIssueSnapshotDto;
import ru.sportsresults.api.dto.GlobalResultIssueCurrentContextDto;
import ru.sportsresults.api.dto.GlobalResultIssueDetailDto;
import ru.sportsresults.api.dto.GlobalResultIssueHistoryDto;
import ru.sportsresults.api.dto.GlobalResultIssueJournalItemDto;
import ru.sportsresults.api.dto.PageResponse;
import ru.sportsresults.api.dto.RankingAchievementDto;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueAttachment;
import ru.sportsresults.domain.ResultIssueHistory;
import ru.sportsresults.domain.ResultIssueQueueScope;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;
import ru.sportsresults.repository.GlobalResultIssueJournalProjection;
import ru.sportsresults.repository.ResultIssueAttachmentRepository;
import ru.sportsresults.repository.ResultIssueHistoryRepository;
import ru.sportsresults.repository.ResultIssueJournalFilter;
import ru.sportsresults.repository.ResultIssueJournalQuery;
import ru.sportsresults.repository.ResultIssueJournalSort;
import ru.sportsresults.repository.ResultIssueRequestRepository;
import ru.sportsresults.repository.ResultRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class GlobalResultIssueJournalService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    private final ResultIssueRequestRepository issueRepository;
    private final ResultIssueAttachmentRepository attachmentRepository;
    private final ResultIssueHistoryRepository historyRepository;
    private final ResultRepository resultRepository;
    private final ObjectMapper objectMapper;

    public GlobalResultIssueJournalService(
            ResultIssueRequestRepository issueRepository,
            ResultIssueAttachmentRepository attachmentRepository,
            ResultIssueHistoryRepository historyRepository,
            ResultRepository resultRepository,
            ObjectMapper objectMapper
    ) {
        this.issueRepository = issueRepository;
        this.attachmentRepository = attachmentRepository;
        this.historyRepository = historyRepository;
        this.resultRepository = resultRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<GlobalResultIssueJournalItemDto> search(
            Long issueId,
            Long eventId,
            String location,
            LocalDate eventDateFrom,
            LocalDate eventDateTo,
            Instant createdFrom,
            Instant createdTo,
            Long raceId,
            String raceCode,
            String bib,
            String participant,
            ResultIssueType issueType,
            ResultCorrectionReason correctionReason,
            List<ResultIssueStatus> statuses,
            ResultIssueQueueScope queueScope,
            ResultIssueArchiveReason queueArchiveReason,
            UUID queueArchivedImportOperationId,
            int page,
            int size,
            String sortValue,
            String directionValue
    ) {
        validatePage(page, size);
        ResultIssueJournalQuery query = prepareQuery(
                issueId, eventId, location, eventDateFrom, eventDateTo, createdFrom, createdTo,
                raceId, raceCode, bib, participant, issueType,
                correctionReason, statuses, queueScope, queueArchiveReason,
                queueArchivedImportOperationId, sortValue, directionValue
        );
        return search(query.filter(), page, size, query.sort(), query.direction());
    }

    public ResultIssueJournalQuery prepareQuery(
            Long issueId,
            Long eventId,
            String location,
            LocalDate eventDateFrom,
            LocalDate eventDateTo,
            Instant createdFrom,
            Instant createdTo,
            Long raceId,
            String raceCode,
            String bib,
            String participant,
            ResultIssueType issueType,
            ResultCorrectionReason correctionReason,
            List<ResultIssueStatus> statuses,
            ResultIssueQueueScope queueScope,
            ResultIssueArchiveReason queueArchiveReason,
            UUID queueArchivedImportOperationId,
            String sortValue,
            String directionValue
    ) {
        validatePositive("issueId", issueId);
        validatePositive("eventId", eventId);
        validatePositive("raceId", raceId);
        validateRanges(eventDateFrom, eventDateTo, createdFrom, createdTo);

        ResultIssueJournalSort sort = ResultIssueJournalSort.parse(sortValue);
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(directionValue);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException(
                    "INVALID_JOURNAL_SORT_DIRECTION", "direction must be asc or desc"
            );
        }
        ResultIssueJournalFilter filter = new ResultIssueJournalFilter(
                issueId,
                eventId,
                normalize(location, 255, "location"),
                atUtcStart(eventDateFrom),
                afterUtcEnd(eventDateTo),
                createdFrom,
                createdTo,
                raceId,
                normalize(raceCode, 255, "raceCode"),
                normalize(bib, 64, "bib"),
                normalize(participant, 320, "participant"),
                issueType,
                correctionReason,
                statuses == null ? Set.of() : new LinkedHashSet<>(statuses),
                queueScope == null ? ResultIssueQueueScope.ALL : queueScope,
                queueArchiveReason,
                queueArchivedImportOperationId
        );
        return new ResultIssueJournalQuery(filter, sort, direction);
    }

    /** Reusable read path for later journal exports. */
    @Transactional(readOnly = true)
    public PageResponse<GlobalResultIssueJournalItemDto> search(
            ResultIssueJournalFilter filter,
            int page,
            int size,
            ResultIssueJournalSort sort,
            Sort.Direction direction
    ) {
        Page<GlobalResultIssueJournalProjection> result = issueRepository.searchJournal(
                filter, page, size, sort, direction
        );
        return new PageResponse<>(
                result.getContent().stream().map(GlobalResultIssueJournalService::toListItem).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages(),
                sort.apiName(),
                direction.name().toLowerCase(Locale.ROOT)
        );
    }

    @Transactional(readOnly = true)
    public GlobalResultIssueDetailDto get(Long issueId) {
        validatePositive("issueId", issueId);
        ResultIssueRequest issue = issueRepository.findJournalDetailById(issueId)
                .orElseThrow(GlobalResultIssueJournalService::notFound);
        List<ResultIssueAttachment> attachments =
                attachmentRepository.findAllByIssueRequest_IdOrderByCreatedAtAscIdAsc(issueId);
        List<ResultIssueHistory> history =
                historyRepository.findAllByIssueRequest_IdOrderByCreatedAtAscIdAsc(issueId);
        return toDetail(issue, attachments, history);
    }

    private GlobalResultIssueDetailDto toDetail(
            ResultIssueRequest issue,
            List<ResultIssueAttachment> attachments,
            List<ResultIssueHistory> history
    ) {
        Registration registration = issue.getRegistration();
        Race race = registration.getRace();
        Category category = registration.getCategory();
        Result currentResult = resultRepository.findByRegistrationId(registration.getId()).orElse(null);
        return new GlobalResultIssueDetailDto(
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
                issue.getQueueArchivedAt(),
                issue.getQueueArchivedBy(),
                issue.getQueueArchiveReason(),
                issue.getQueueArchivedImportOperationId(),
                snapshot(issue),
                new GlobalResultIssueCurrentContextDto(
                        true,
                        !registration.isCurrent(),
                        registration.getId(),
                        new GlobalResultIssueCurrentContextDto.CurrentRace(
                                race.getId(), race.getName(), race.getSourceCode(), race.getDistanceMeters()
                        ),
                        category == null ? null : new GlobalResultIssueCurrentContextDto.CurrentCategory(
                                category.getId(), category.getDisplayName()
                        ),
                        currentResult == null ? null : new GlobalResultIssueCurrentContextDto.CurrentResult(
                                currentResult.getId(),
                                currentResult.getStatus(),
                                milliseconds(currentResult.getGunTime()),
                                milliseconds(currentResult.getChipTime())
                        )
                ),
                attachments.size(),
                attachments.stream().map(GlobalResultIssueJournalService::toAttachment).toList(),
                history.stream().map(GlobalResultIssueJournalService::toHistory).toList()
        );
    }

    private AdminResultIssueSnapshotDto snapshot(ResultIssueRequest issue) {
        return new AdminResultIssueSnapshotDto(
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

    private static GlobalResultIssueJournalItemDto toListItem(GlobalResultIssueJournalProjection item) {
        return new GlobalResultIssueJournalItemDto(
                item.issueId(),
                item.issueType(),
                item.correctionReason(),
                item.status(),
                item.createdAt(),
                item.updatedAt(),
                item.queueArchivedAt(),
                item.queueArchivedBy(),
                item.queueArchiveReason(),
                item.queueArchivedImportOperationId(),
                new GlobalResultIssueJournalItemDto.HistoricalEvent(
                        item.eventId(), item.eventName(), item.eventLocation(), item.eventStartsAt()
                ),
                new GlobalResultIssueJournalItemDto.HistoricalSportFormat(
                        item.sportFormatId(), item.sportFormatName(), item.sportFormatCode()
                ),
                new GlobalResultIssueJournalItemDto.HistoricalRace(
                        item.raceId(), item.raceName(), item.raceCode(), item.raceDistanceMeters()
                ),
                new GlobalResultIssueJournalItemDto.HistoricalParticipant(
                        item.registrationId(), item.bib(), item.displayName()
                ),
                new GlobalResultIssueJournalItemDto.HistoricalResult(
                        item.observedResultStatus(),
                        milliseconds(item.observedGunTime()),
                        milliseconds(item.observedChipTime())
                ),
                item.snapshotOrigin(),
                item.categoryName(),
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

    private static GlobalResultIssueHistoryDto toHistory(ResultIssueHistory history) {
        return new GlobalResultIssueHistoryDto(
                history.getId(),
                history.getAction(),
                history.getFromStatus(),
                history.getToStatus(),
                history.getActor(),
                history.getReason(),
                history.getCreatedAt()
        );
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || (long) page * size > Integer.MAX_VALUE) {
            throw new InvalidRequestException("INVALID_PAGE", "page is outside the supported range");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "INVALID_PAGE_SIZE", "size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
    }

    private static void validatePositive(String field, Long value) {
        if (value != null && value < 1) {
            throw new InvalidRequestException(
                    "INVALID_JOURNAL_FILTER", field + " must be positive"
            );
        }
    }

    private static void validateRanges(
            LocalDate eventDateFrom,
            LocalDate eventDateTo,
            Instant createdFrom,
            Instant createdTo
    ) {
        if (eventDateFrom != null && eventDateTo != null && eventDateFrom.isAfter(eventDateTo)) {
            throw new InvalidRequestException(
                    "INVALID_EVENT_DATE_RANGE", "eventDateFrom must not be after eventDateTo"
            );
        }
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new InvalidRequestException(
                    "INVALID_CREATED_DATE_RANGE", "createdFrom must not be after createdTo"
            );
        }
    }

    private static String normalize(String value, int maxLength, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new InvalidRequestException(
                    "INVALID_JOURNAL_FILTER", field + " must not exceed " + maxLength + " characters"
            );
        }
        return normalized;
    }

    private static Instant atUtcStart(LocalDate value) {
        return value == null ? null : value.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant afterUtcEnd(LocalDate value) {
        if (value == null) {
            return null;
        }
        try {
            return value.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeException exception) {
            throw new InvalidRequestException("INVALID_EVENT_DATE_RANGE", "eventDateTo is outside the supported range");
        }
    }

    private static Long milliseconds(Duration duration) {
        return duration == null ? null : duration.toMillis();
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException(
                "RESULT_ISSUE_NOT_FOUND", "Result issue request not found"
        );
    }
}
