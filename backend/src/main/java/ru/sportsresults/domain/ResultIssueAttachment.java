package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Entity
@Table(name = "result_issue_attachments")
public class ResultIssueAttachment extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_request_id", nullable = false)
    private ResultIssueRequest issueRequest;

    @NotBlank
    @Size(max = 512)
    @Column(name = "original_file_name", nullable = false, length = 512)
    private String originalFileName;

    @NotBlank
    @Size(max = 768)
    @Column(name = "storage_key", nullable = false, length = 768, unique = true)
    private String storageKey;

    @Size(max = 255)
    @Column(name = "content_type", length = 255)
    private String contentType;

    @Size(max = 255)
    @Column(name = "detected_content_type", length = 255)
    private String detectedContentType;

    @Positive
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Size(max = 512)
    @Column(name = "storage_etag", length = 512)
    private String storageEtag;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 32)
    private AttachmentUploadStatus uploadStatus = AttachmentUploadStatus.PENDING_UPLOAD;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "scan_status", nullable = false, length = 32)
    private AttachmentScanStatus scanStatus = AttachmentScanStatus.PENDING;

    @NotNull
    @Column(name = "upload_authorization_expires_at", nullable = false)
    private Instant uploadAuthorizationExpiresAt;

    @NotNull
    @Column(name = "retention_expires_at", nullable = false)
    private Instant retentionExpiresAt;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "scanned_at")
    private Instant scannedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public ResultIssueRequest getIssueRequest() { return issueRequest; }
    public void setIssueRequest(ResultIssueRequest issueRequest) { this.issueRequest = issueRequest; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getDetectedContentType() { return detectedContentType; }
    public void setDetectedContentType(String detectedContentType) { this.detectedContentType = detectedContentType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getStorageEtag() { return storageEtag; }
    public void setStorageEtag(String storageEtag) { this.storageEtag = storageEtag; }
    public AttachmentUploadStatus getUploadStatus() { return uploadStatus; }
    public void setUploadStatus(AttachmentUploadStatus uploadStatus) { this.uploadStatus = uploadStatus; }
    public AttachmentScanStatus getScanStatus() { return scanStatus; }
    public void setScanStatus(AttachmentScanStatus scanStatus) { this.scanStatus = scanStatus; }
    public Instant getUploadAuthorizationExpiresAt() { return uploadAuthorizationExpiresAt; }
    public void setUploadAuthorizationExpiresAt(Instant value) { this.uploadAuthorizationExpiresAt = value; }
    public Instant getRetentionExpiresAt() { return retentionExpiresAt; }
    public void setRetentionExpiresAt(Instant retentionExpiresAt) { this.retentionExpiresAt = retentionExpiresAt; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
    public Instant getScannedAt() { return scannedAt; }
    public void setScannedAt(Instant scannedAt) { this.scannedAt = scannedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
