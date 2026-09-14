package ru.sportsresults.scanning.attachments;

import ru.sportsresults.domain.AttachmentScanStatus;

public enum FakeAttachmentScanResult {
    CLEAN,
    INFECTED,
    SCAN_FAILED;

    AttachmentScanStatus toDomainStatus() {
        return AttachmentScanStatus.valueOf(name());
    }
}
