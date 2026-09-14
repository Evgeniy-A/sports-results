package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.ResultIssueAttachmentShareGrantDto;
import ru.sportsresults.api.dto.ResultIssueShareBatchDto;
import ru.sportsresults.service.ResultIssueShareAdminService;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "basicAuth")
public class ResultIssueShareAdminController {

    private final ResultIssueShareAdminService service;

    public ResultIssueShareAdminController(ResultIssueShareAdminService service) {
        this.service = service;
    }

    @GetMapping("/result-issue-share-batches/{batchId}")
    @Operation(summary = "Read Result Issue attachment share batch audit information")
    public ResultIssueShareBatchDto getBatch(@PathVariable UUID batchId) {
        return service.getBatch(batchId);
    }

    @PostMapping("/result-issue-share-batches/{batchId}/revoke")
    @Operation(summary = "Revoke all attachment links created by one Result Issue journal export")
    public ResultIssueShareBatchDto revokeBatch(@PathVariable UUID batchId, Principal principal) {
        return service.revokeBatch(batchId, principal.getName());
    }

    @PostMapping("/result-issue-attachment-share-grants/{grantId}/revoke")
    @Operation(summary = "Revoke one Result Issue attachment share link")
    public ResultIssueAttachmentShareGrantDto revokeGrant(@PathVariable UUID grantId, Principal principal) {
        return service.revokeGrant(grantId, principal.getName());
    }
}
