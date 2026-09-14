package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;
import ru.sportsresults.domain.ResultIssueAttachment;
import ru.sportsresults.repository.ResultIssueAttachmentRepository;
import ru.sportsresults.scanning.attachments.AttachmentScanCandidate;
import ru.sportsresults.scanning.attachments.AttachmentSecurityScanner;

import java.util.Optional;

@Service
public class ResultIssueAttachmentScanOrchestrator {

    private final ResultIssueAttachmentRepository attachmentRepository;
    private final ResultIssueAttachmentScanService scanService;
    private final Optional<AttachmentSecurityScanner> scanner;

    public ResultIssueAttachmentScanOrchestrator(
            ResultIssueAttachmentRepository attachmentRepository,
            ResultIssueAttachmentScanService scanService,
            Optional<AttachmentSecurityScanner> scanner
    ) {
        this.attachmentRepository = attachmentRepository;
        this.scanService = scanService;
        this.scanner = scanner;
    }

    public AttachmentScanExecution scan(Long attachmentId) {
        AttachmentSecurityScanner configuredScanner = scanner.orElseThrow(() ->
                new RequestConflictException(
                        "ATTACHMENT_SCANNER_NOT_CONFIGURED", "No attachment security scanner is configured"
                )
        );
        ResultIssueAttachment attachment = requireAttachment(attachmentId);
        if (attachment.getUploadStatus() != AttachmentUploadStatus.UPLOADED) {
            throw new RequestConflictException(
                    "ATTACHMENT_NOT_UPLOADED", "Only uploaded attachments can be scanned"
            );
        }
        if (attachment.getScanStatus() == AttachmentScanStatus.CLEAN) {
            throw new RequestConflictException(
                    "ATTACHMENT_SCAN_ALREADY_CLEAN", "A clean attachment cannot be scanned again"
            );
        }

        var result = configuredScanner.scan(new AttachmentScanCandidate(
                attachment.getId(),
                attachment.getStorageKey(),
                attachment.getContentType(),
                attachment.getSizeBytes()
        ));
        scanService.recordScanResult(attachmentId, result.status(), result.detectedContentType());

        ResultIssueAttachment scanned = requireAttachment(attachmentId);
        return new AttachmentScanExecution(
                scanned.getId(), scanned.getUploadStatus(), scanned.getScanStatus(), scanned.getScannedAt()
        );
    }

    private ResultIssueAttachment requireAttachment(Long attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ATTACHMENT_NOT_FOUND", "Attachment not found"
                ));
    }
}
