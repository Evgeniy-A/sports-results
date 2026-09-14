package ru.sportsresults.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.CreateResultIssueAttachmentRequest;
import ru.sportsresults.api.dto.ResultIssueAttachmentStatusDto;
import ru.sportsresults.api.dto.ResultIssueAttachmentUploadDto;
import ru.sportsresults.service.ResultIssueAttachmentService;

@RestController
@RequestMapping("/api/result-issues/{issueId}/attachments")
public class ResultIssueAttachmentController {

    public static final String CAPABILITY_HEADER = "X-Result-Issue-Upload-Token";

    private final ResultIssueAttachmentService attachmentService;

    public ResultIssueAttachmentController(ResultIssueAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResultIssueAttachmentUploadDto create(
            @PathVariable Long issueId,
            @RequestHeader(name = CAPABILITY_HEADER, required = false) String capabilityToken,
            @Valid @RequestBody CreateResultIssueAttachmentRequest request
    ) {
        return attachmentService.create(issueId, capabilityToken, request);
    }

    @PostMapping("/{attachmentId}/confirm")
    public ResultIssueAttachmentStatusDto confirm(
            @PathVariable Long issueId,
            @PathVariable Long attachmentId,
            @RequestHeader(name = CAPABILITY_HEADER, required = false) String capabilityToken
    ) {
        return attachmentService.confirmUpload(issueId, attachmentId, capabilityToken);
    }

    @PostMapping("/{attachmentId}/authorize")
    public ResultIssueAttachmentUploadDto authorize(
            @PathVariable Long issueId,
            @PathVariable Long attachmentId,
            @RequestHeader(name = CAPABILITY_HEADER, required = false) String capabilityToken
    ) {
        return attachmentService.refreshUploadAuthorization(issueId, attachmentId, capabilityToken);
    }
}
