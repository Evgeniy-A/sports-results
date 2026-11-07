package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.ResetResultIssueWorkItemRequest;
import ru.sportsresults.api.dto.ResultIssueWorkItemDto;
import ru.sportsresults.api.dto.ResultIssueWorkspaceRowDto;
import ru.sportsresults.api.dto.ResultIssueWorkspaceSheetDto;
import ru.sportsresults.api.dto.ResultIssueWorkspaceSyncDto;
import ru.sportsresults.api.dto.UpdateResultIssueWorkItemRequest;
import ru.sportsresults.repository.ResultIssueWorkspaceQueryRepository;
import ru.sportsresults.service.ResultIssueWorkItemService;
import ru.sportsresults.service.ResultIssueWorkSyncService;

import java.security.Principal;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin/result-issue-workspace")
@SecurityRequirement(name = "basicAuth")
public class ResultIssueWorkspaceController {

    private final ResultIssueWorkspaceQueryRepository queryRepository;
    private final ResultIssueWorkItemService workItemService;
    private final ResultIssueWorkSyncService syncService;

    public ResultIssueWorkspaceController(
            ResultIssueWorkspaceQueryRepository queryRepository,
            ResultIssueWorkItemService workItemService,
            ResultIssueWorkSyncService syncService
    ) {
        this.queryRepository = queryRepository;
        this.workItemService = workItemService;
        this.syncService = syncService;
    }

    @GetMapping("/sheets")
    public List<ResultIssueWorkspaceSheetDto> sheets() {
        return queryRepository.findSheets();
    }

    @GetMapping("/sheets/{seriesId}/{year}/rows")
    public List<ResultIssueWorkspaceRowDto> rows(
            @PathVariable @Positive long seriesId,
            @PathVariable @Min(2000) @Max(2200) int year
    ) {
        return queryRepository.findRows(seriesId, year);
    }

    @PutMapping("/issues/{issueId}")
    public ResultIssueWorkItemDto update(
            @PathVariable @Positive Long issueId,
            @Valid @RequestBody UpdateResultIssueWorkItemRequest request,
            Principal principal
    ) {
        return workItemService.update(issueId, request, principal.getName());
    }

    @PostMapping("/issues/{issueId}/reset")
    public ResultIssueWorkItemDto reset(
            @PathVariable @Positive Long issueId,
            @Valid @RequestBody ResetResultIssueWorkItemRequest request,
            Principal principal
    ) {
        return workItemService.reset(issueId, request, principal.getName());
    }

    @PostMapping("/sheets/{seriesId}/{year}/sync")
    public ResultIssueWorkspaceSyncDto sync(
            @PathVariable @Positive long seriesId,
            @PathVariable @Min(2000) @Max(2200) int year,
            Principal principal
    ) {
        return syncService.syncSheet(seriesId, year, principal.getName());
    }
}
