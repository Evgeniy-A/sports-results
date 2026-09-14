package ru.sportsresults.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.ResultIssueAttachmentCapabilitiesDto;
import ru.sportsresults.config.ResultIssueAttachmentProperties;
import ru.sportsresults.storage.attachments.AttachmentObjectStorage;

@RestController
@RequestMapping("/api/result-issue-attachments")
public class ResultIssueAttachmentCapabilitiesController {

    private final AttachmentObjectStorage objectStorage;
    private final ResultIssueAttachmentProperties properties;

    public ResultIssueAttachmentCapabilitiesController(
            AttachmentObjectStorage objectStorage,
            ResultIssueAttachmentProperties properties
    ) {
        this.objectStorage = objectStorage;
        this.properties = properties;
    }

    @GetMapping("/capabilities")
    public ResultIssueAttachmentCapabilitiesDto capabilities() {
        return new ResultIssueAttachmentCapabilitiesDto(
                objectStorage.supportsDirectBrowserUpload(),
                properties.maxFileSizeBytes(),
                properties.maxAttachmentsPerIssue()
        );
    }
}
