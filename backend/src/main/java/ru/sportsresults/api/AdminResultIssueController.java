package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.AdminResultIssueAttachmentDownloadDto;
import ru.sportsresults.api.dto.AdminResultIssueArchiveDto;
import ru.sportsresults.api.dto.AdminResultIssueDetailDto;
import ru.sportsresults.api.dto.AdminResultIssueListItemDto;
import ru.sportsresults.api.dto.AdminResultIssueStatusDto;
import ru.sportsresults.api.dto.PageResponse;
import ru.sportsresults.api.dto.UpdateResultIssueStatusRequest;
import ru.sportsresults.api.dto.ArchiveResultIssueRequest;
import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;
import ru.sportsresults.domain.ResultIssueQueueScope;
import ru.sportsresults.service.AdminResultIssueService;
import ru.sportsresults.service.ResultIssueLifecycleService;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/events/{eventId}/result-issue-requests")
@SecurityRequirement(name = "basicAuth")
public class AdminResultIssueController {

    private final AdminResultIssueService service;
    private final ResultIssueLifecycleService lifecycleService;

    public AdminResultIssueController(
            AdminResultIssueService service,
            ResultIssueLifecycleService lifecycleService
    ) {
        this.service = service;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    public PageResponse<AdminResultIssueListItemDto> search(
            @PathVariable Long eventId,
            @RequestParam(required = false) ResultIssueStatus status,
            @RequestParam(required = false) ResultIssueType issueType,
            @RequestParam(required = false) Long issueIdFrom,
            @RequestParam(required = false) Long issueIdTo,
            @RequestParam(required = false) String bib,
            @RequestParam(required = false) ResultCorrectionReason correctionReason,
            @RequestParam(defaultValue = "CURRENT") ResultIssueQueueScope queueScope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return service.search(
                eventId, status, issueType, issueIdFrom, issueIdTo, bib, correctionReason,
                queueScope, page, size
        );
    }

    @GetMapping("/{issueId}")
    public AdminResultIssueDetailDto get(
            @PathVariable Long eventId,
            @PathVariable Long issueId
    ) {
        return service.get(eventId, issueId);
    }

    @PutMapping("/{issueId}/status")
    public AdminResultIssueStatusDto updateStatus(
            @PathVariable Long eventId,
            @PathVariable Long issueId,
            @Valid @RequestBody UpdateResultIssueStatusRequest request,
            Principal principal
    ) {
        return service.updateStatus(eventId, issueId, request, principal.getName());
    }

    @PostMapping("/{issueId}/archive")
    public AdminResultIssueArchiveDto archive(
            @PathVariable Long eventId,
            @PathVariable Long issueId,
            @Valid @RequestBody ArchiveResultIssueRequest request,
            Principal principal
    ) {
        return lifecycleService.archiveManual(eventId, issueId, request.reason(), principal.getName());
    }

    @PostMapping("/{issueId}/attachments/{attachmentId}/download-authorization")
    public AdminResultIssueAttachmentDownloadDto prepareAttachmentDownload(
            @PathVariable Long eventId,
            @PathVariable Long issueId,
            @PathVariable Long attachmentId
    ) {
        return service.prepareAttachmentDownload(eventId, issueId, attachmentId);
    }
}
