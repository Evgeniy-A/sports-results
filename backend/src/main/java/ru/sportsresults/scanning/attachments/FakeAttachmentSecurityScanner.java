package ru.sportsresults.scanning.attachments;

/**
 * Development/test simulator. It does not inspect bytes and is not malware protection.
 */
public final class FakeAttachmentSecurityScanner implements AttachmentSecurityScanner {

    private final FakeAttachmentScannerProperties properties;

    public FakeAttachmentSecurityScanner(FakeAttachmentScannerProperties properties) {
        this.properties = properties;
    }

    @Override
    public AttachmentSecurityScanResult scan(AttachmentScanCandidate candidate) {
        return new AttachmentSecurityScanResult(properties.result().toDomainStatus(), null);
    }
}
