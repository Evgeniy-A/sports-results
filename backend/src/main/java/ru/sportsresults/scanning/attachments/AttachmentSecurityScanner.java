package ru.sportsresults.scanning.attachments;

public interface AttachmentSecurityScanner {

    AttachmentSecurityScanResult scan(AttachmentScanCandidate candidate);
}
