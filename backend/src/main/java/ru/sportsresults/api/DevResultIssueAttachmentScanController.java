package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.DevResultIssueAttachmentScanDto;
import ru.sportsresults.service.AttachmentScanExecution;
import ru.sportsresults.service.ResultIssueAttachmentScanOrchestrator;

@RestController
@Profile("local")
@ConditionalOnProperty(
        prefix = "app.result-issues.attachments",
        name = "scanner-provider",
        havingValue = "fake"
)
@RequestMapping("/api/admin/dev/result-issue-attachments")
@SecurityRequirement(name = "basicAuth")
public class DevResultIssueAttachmentScanController {

    private final ResultIssueAttachmentScanOrchestrator orchestrator;

    public DevResultIssueAttachmentScanController(ResultIssueAttachmentScanOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/{attachmentId}/scan")
    public DevResultIssueAttachmentScanDto scan(@PathVariable Long attachmentId) {
        AttachmentScanExecution result = orchestrator.scan(attachmentId);
        return new DevResultIssueAttachmentScanDto(
                result.attachmentId(), result.uploadStatus(), result.scanStatus(), result.scannedAt()
        );
    }
}
