package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;
import ru.sportsresults.domain.ResultIssueAttachment;
import ru.sportsresults.repository.ResultIssueAttachmentRepository;

import java.time.Instant;

@Service
public class ResultIssueAttachmentScanService {

    private final ResultIssueAttachmentRepository attachmentRepository;

    public ResultIssueAttachmentScanService(ResultIssueAttachmentRepository attachmentRepository) {
        this.attachmentRepository = attachmentRepository;
    }

    @Transactional
    public void recordScanResult(
            Long attachmentId,
            AttachmentScanStatus scanStatus,
            String detectedContentType
    ) {
        if (scanStatus == null || scanStatus == AttachmentScanStatus.PENDING) {
            throw new InvalidRequestException(
                    "INVALID_SCAN_RESULT", "Scanner must report a terminal scan status"
            );
        }
        ResultIssueAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ATTACHMENT_NOT_FOUND", "Attachment not found"
                ));
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
        attachment.setScanStatus(scanStatus);
        attachment.setDetectedContentType(normalize(detectedContentType));
        attachment.setScannedAt(Instant.now());
        attachmentRepository.saveAndFlush(attachment);
    }

    private static String normalize(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        String normalized = contentType.strip().toLowerCase(java.util.Locale.ROOT);
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }
}
