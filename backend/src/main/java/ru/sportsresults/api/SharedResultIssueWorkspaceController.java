package ru.sportsresults.api;

import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.AdminResultIssueAttachmentDownloadDto;
import ru.sportsresults.api.dto.AdminResultIssueStatusDto;
import ru.sportsresults.api.dto.GlobalResultIssueDetailDto;
import ru.sportsresults.api.dto.GlobalResultIssueHistoryDto;
import ru.sportsresults.api.dto.JournalShareDtos;
import ru.sportsresults.api.dto.RaceDto;
import ru.sportsresults.api.dto.ResetResultIssueWorkItemRequest;
import ru.sportsresults.api.dto.ResultIssueWorkItemDto;
import ru.sportsresults.api.dto.ResultIssueWorkspaceRowDto;
import ru.sportsresults.api.dto.ResultIssueWorkspaceSheetDto;
import ru.sportsresults.api.dto.ResultIssueWorkspaceSyncDto;
import ru.sportsresults.api.dto.UpdateResultIssueStatusRequest;
import ru.sportsresults.api.dto.UpdateResultIssueWorkItemRequest;
import ru.sportsresults.repository.ResultIssueRequestRepository;
import ru.sportsresults.repository.ResultIssueWorkspaceQueryRepository;
import ru.sportsresults.service.AdminResultIssueService;
import ru.sportsresults.service.EventService;
import ru.sportsresults.service.GlobalResultIssueJournalService;
import ru.sportsresults.service.JournalShareAccessService;
import ru.sportsresults.service.ResourceNotFoundException;
import ru.sportsresults.service.ResultIssueWorkItemService;
import ru.sportsresults.service.ResultIssueWorkSyncService;

import java.util.List;

@RestController
@RequestMapping("/api/shared/result-issue-workspace")
public class SharedResultIssueWorkspaceController {

    public static final String SESSION_HEADER = "X-Journal-Share-Session";

    private final JournalShareAccessService accessService;
    private final ResultIssueWorkspaceQueryRepository queryRepository;
    private final ResultIssueWorkItemService workItemService;
    private final ResultIssueWorkSyncService syncService;
    private final GlobalResultIssueJournalService journalService;
    private final AdminResultIssueService issueService;
    private final ResultIssueRequestRepository issueRepository;
    private final EventService eventService;

    public SharedResultIssueWorkspaceController(
            JournalShareAccessService accessService,
            ResultIssueWorkspaceQueryRepository queryRepository,
            ResultIssueWorkItemService workItemService,
            ResultIssueWorkSyncService syncService,
            GlobalResultIssueJournalService journalService,
            AdminResultIssueService issueService,
            ResultIssueRequestRepository issueRepository,
            EventService eventService
    ) {
        this.accessService = accessService;
        this.queryRepository = queryRepository;
        this.workItemService = workItemService;
        this.syncService = syncService;
        this.journalService = journalService;
        this.issueService = issueService;
        this.issueRepository = issueRepository;
        this.eventService = eventService;
    }

    @PostMapping("/exchange")
    public JournalShareDtos.ExchangeResponse exchange(
            @Valid @RequestBody JournalShareDtos.ExchangeRequest request
    ) {
        return accessService.exchange(request.token());
    }

    @GetMapping("/context")
    public JournalShareDtos.Context context(@RequestHeader(SESSION_HEADER) String token) {
        return accessService.authenticate(token).dto();
    }

    @GetMapping("/sheets")
    public List<ResultIssueWorkspaceSheetDto> sheets(@RequestHeader(SESSION_HEADER) String token) {
        var access = accessService.authenticate(token);
        ResultIssueWorkspaceSheetDto sheet = queryRepository.findSheet(
                access.seriesId(), access.year(), access.cityScope());
        return sheet == null ? List.of() : List.of(sheet);
    }

    @GetMapping("/rows")
    public List<ResultIssueWorkspaceRowDto> rows(@RequestHeader(SESSION_HEADER) String token) {
        var access = accessService.authenticate(token);
        return queryRepository.findRows(access.seriesId(), access.year(), access.cityScope()).stream()
                .map(SharedResultIssueWorkspaceController::withoutAssignee)
                .toList();
    }

    @GetMapping("/events/{eventId}/races")
    public List<RaceDto> races(
            @RequestHeader(SESSION_HEADER) String token,
            @PathVariable Long eventId
    ) {
        var access = accessService.authenticate(token);
        requireEventScope(access, eventId);
        return eventService.listAdminEventRaces(eventId);
    }

    @PutMapping("/issues/{issueId}")
    public ResultIssueWorkItemDto update(
            @RequestHeader(SESSION_HEADER) String token,
            @PathVariable Long issueId,
            @Valid @RequestBody UpdateResultIssueWorkItemRequest request
    ) {
        var access = accessService.authenticate(token);
        requireEdit(access);
        requireIssueScope(access, issueId);
        return withoutAssignee(workItemService.updateShared(issueId, request, access.actor()));
    }

    @PostMapping("/issues/{issueId}/reset")
    public ResultIssueWorkItemDto reset(
            @RequestHeader(SESSION_HEADER) String token,
            @PathVariable Long issueId,
            @Valid @RequestBody ResetResultIssueWorkItemRequest request
    ) {
        var access = accessService.authenticate(token);
        requireEdit(access);
        requireIssueScope(access, issueId);
        return withoutAssignee(workItemService.reset(issueId, request, access.actor()));
    }

    @PutMapping("/issues/{issueId}/status")
    public AdminResultIssueStatusDto updateStatus(
            @RequestHeader(SESSION_HEADER) String token,
            @PathVariable Long issueId,
            @Valid @RequestBody UpdateResultIssueStatusRequest request
    ) {
        var access = accessService.authenticate(token);
        requireEdit(access);
        long eventId = requireIssueScope(access, issueId);
        return issueService.updateStatus(eventId, issueId, request, access.actor());
    }

    @PostMapping("/sync")
    public ResultIssueWorkspaceSyncDto sync(@RequestHeader(SESSION_HEADER) String token) {
        var access = accessService.authenticate(token);
        if (!access.canSyncNow()) {
            throw new AccessDeniedException("Shared journal synchronization is not allowed");
        }
        return syncService.syncSheet(
                access.seriesId(), access.year(), access.cityScope(), access.actor());
    }

    @GetMapping("/issues/{issueId}")
    public GlobalResultIssueDetailDto detail(
            @RequestHeader(SESSION_HEADER) String token,
            @PathVariable Long issueId
    ) {
        var access = accessService.authenticate(token);
        requireIssueScope(access, issueId);
        return withoutInternalIdentities(journalService.get(issueId));
    }

    @PostMapping("/issues/{issueId}/attachments/{attachmentId}/download-authorization")
    public AdminResultIssueAttachmentDownloadDto attachment(
            @RequestHeader(SESSION_HEADER) String token,
            @PathVariable Long issueId,
            @PathVariable Long attachmentId
    ) {
        var access = accessService.authenticate(token);
        long eventId = requireIssueScope(access, issueId);
        return issueService.prepareAttachmentDownload(eventId, issueId, attachmentId);
    }

    private long requireIssueScope(JournalShareAccessService.AccessContext access, Long issueId) {
        if (!queryRepository.issueInScope(issueId, access.seriesId(), access.year(), access.cityScope())) {
            throw notFound();
        }
        return issueRepository.findEventIdByIssueId(issueId).orElseThrow(SharedResultIssueWorkspaceController::notFound);
    }

    private void requireEventScope(JournalShareAccessService.AccessContext access, Long eventId) {
        if (!queryRepository.eventInScope(eventId, access.seriesId(), access.year(), access.cityScope())) {
            throw notFound();
        }
    }

    private static void requireEdit(JournalShareAccessService.AccessContext access) {
        if (!access.canEdit()) {
            throw new AccessDeniedException("Shared journal is read-only");
        }
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("JOURNAL_SHARE_RESOURCE_NOT_FOUND", "Shared journal resource not found");
    }

    private static ResultIssueWorkspaceRowDto withoutAssignee(ResultIssueWorkspaceRowDto row) {
        ResultIssueWorkspaceRowDto.WorkItem work = row.workItem();
        if (work == null) {
            return row;
        }
        ResultIssueWorkspaceRowDto.WorkItem safe = new ResultIssueWorkspaceRowDto.WorkItem(
                work.workItemId(), work.desiredResultStatus(), work.desiredGunTimeMs(),
                work.desiredChipTimeMs(), work.desiredDisplayName(), work.desiredGender(),
                work.desiredBirthDate(), work.desiredBib(), work.desiredRaceId(),
                work.desiredRaceName(), work.desiredDistanceMeters(), null, null,
                work.internalComment(), work.syncStatus(), work.syncErrorCode(), work.version(),
                work.updatedAt(), sharedActor(work.updatedBy()), work.syncedAt(), sharedActor(work.syncedBy())
        );
        return new ResultIssueWorkspaceRowDto(
                row.issueId(), row.seriesId(), row.seriesName(), row.eventId(), row.eventName(),
                row.eventYear(), row.location(), row.currentRaceId(), row.currentRaceName(),
                row.currentDistanceMeters(), row.registrationId(), row.registrationCurrent(),
                row.currentBib(), row.currentDisplayName(), row.currentGender(), row.currentBirthDate(),
                row.currentCategoryName(), row.issueType(), row.correctionReason(), row.issueStatus(),
                row.attachmentCount(), row.currentResultId(), row.currentResultStatus(),
                row.currentGunTimeMs(), row.currentChipTimeMs(), row.claimedGunTimeMs(),
                row.claimedChipTimeMs(), safe, row.updatedAt()
        );
    }

    private static ResultIssueWorkItemDto withoutAssignee(ResultIssueWorkItemDto item) {
        return new ResultIssueWorkItemDto(
                item.workItemId(), item.issueId(), item.resultId(), item.desiredResultStatus(),
                item.desiredGunTimeMs(), item.desiredChipTimeMs(), item.desiredDisplayName(),
                item.desiredGender(), item.desiredBirthDate(), item.desiredBib(), item.desiredRaceId(),
                null, null, item.internalComment(), item.syncStatus(), item.syncErrorCode(),
                item.version(), item.updatedAt(), sharedActor(item.updatedBy()),
                item.syncedAt(), sharedActor(item.syncedBy())
        );
    }

    private static GlobalResultIssueDetailDto withoutInternalIdentities(GlobalResultIssueDetailDto detail) {
        return new GlobalResultIssueDetailDto(
                detail.issueId(), detail.eventId(), detail.issueType(), detail.correctionReason(), detail.status(),
                detail.contactEmail(), detail.message(), detail.createdAt(), detail.updatedAt(), detail.resolvedAt(),
                detail.claimedGunTimeMs(), detail.claimedChipTimeMs(), detail.estimatedStartAt(),
                detail.estimatedFinishAt(), detail.queueArchivedAt(), sharedActor(detail.queueArchivedBy()),
                detail.queueArchiveReason(), detail.queueArchivedImportOperationId(), detail.historicalSnapshot(),
                detail.currentContext(), detail.attachmentCount(), detail.attachments(),
                detail.history().stream().map(history -> new GlobalResultIssueHistoryDto(
                        history.historyId(), history.action(), history.fromStatus(), history.toStatus(),
                        sharedActor(history.actor()), history.reason(), history.createdAt()
                )).toList()
        );
    }

    private static String sharedActor(String actor) {
        if (actor == null) {
            return null;
        }
        if (actor.startsWith("SHARE:")) {
            return "SHARED_ACCESS";
        }
        if ("PUBLIC_API".equals(actor)) {
            return actor;
        }
        return "STAFF";
    }
}
