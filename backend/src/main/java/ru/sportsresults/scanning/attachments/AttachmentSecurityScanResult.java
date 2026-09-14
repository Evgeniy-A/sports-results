package ru.sportsresults.scanning.attachments;

import ru.sportsresults.domain.AttachmentScanStatus;

public record AttachmentSecurityScanResult(
        AttachmentScanStatus status,
        String detectedContentType
) {
    public AttachmentSecurityScanResult {
        if (status == null || status == AttachmentScanStatus.PENDING) {
            throw new IllegalArgumentException("A security scanner must return a terminal status");
        }
    }
}
