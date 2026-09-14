package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.GlobalResultIssueDetailDto;
import ru.sportsresults.api.dto.GlobalResultIssueJournalItemDto;
import ru.sportsresults.api.dto.PageResponse;
import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueQueueScope;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;
import ru.sportsresults.service.GlobalResultIssueJournalService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/result-issue-requests")
@SecurityRequirement(name = "basicAuth")
public class GlobalResultIssueJournalController {

    private final GlobalResultIssueJournalService service;

    public GlobalResultIssueJournalController(GlobalResultIssueJournalService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search the global historical Result Issue journal")
    public PageResponse<GlobalResultIssueJournalItemDto> search(
            @RequestParam(required = false) Long issueId,
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) String location,
            @Parameter(description = "Historical Event start date from, interpreted in UTC")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDateFrom,
            @Parameter(description = "Historical Event start date through, interpreted in UTC")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventDateTo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(required = false) Long sportFormatId,
            @RequestParam(required = false) String sportFormatCode,
            @RequestParam(required = false) Long raceId,
            @RequestParam(required = false) String raceCode,
            @RequestParam(required = false) String bib,
            @RequestParam(required = false) String participant,
            @RequestParam(required = false) ResultIssueType issueType,
            @RequestParam(required = false) ResultCorrectionReason correctionReason,
            @Parameter(description = "Repeat status to select multiple workflow statuses")
            @RequestParam(required = false) List<ResultIssueStatus> status,
            @RequestParam(defaultValue = "ALL") ResultIssueQueueScope queueScope,
            @RequestParam(required = false) ResultIssueArchiveReason queueArchiveReason,
            @RequestParam(required = false) UUID queueArchivedImportOperationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return service.search(
                issueId,
                eventId,
                location,
                eventDateFrom,
                eventDateTo,
                createdFrom,
                createdTo,
                sportFormatId,
                sportFormatCode,
                raceId,
                raceCode,
                bib,
                participant,
                issueType,
                correctionReason,
                status,
                queueScope,
                queueArchiveReason,
                queueArchivedImportOperationId,
                page,
                size,
                sort,
                direction
        );
    }

    @GetMapping("/{issueId}")
    @Operation(summary = "Read one Result Issue with snapshot, current context, history and attachment metadata")
    public GlobalResultIssueDetailDto get(@PathVariable Long issueId) {
        return service.get(issueId);
    }
}
