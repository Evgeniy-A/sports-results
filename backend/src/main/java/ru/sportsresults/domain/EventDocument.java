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
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Entity
@Table(name = "event_documents")
public class EventDocument extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EventDocumentType type;

    @NotBlank
    @Size(max = 255)
    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @NotBlank
    @Size(max = 255)
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @NotBlank
    @Size(max = 500)
    @Column(name = "storage_key", nullable = false, length = 500, unique = true)
    private String storageKey;

    @NotBlank
    @Size(max = 100)
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Positive
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @NotBlank
    @Size(min = 64, max = 64)
    @Column(nullable = false, length = 64)
    private String sha256;

    @PositiveOrZero
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "is_public", nullable = false)
    private boolean publicDocument = true;

    @NotNull
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Size(max = 160)
    @Column(name = "uploaded_by", length = 160)
    private String uploadedBy;

    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public EventDocumentType getType() { return type; }
    public void setType(EventDocumentType type) { this.type = type; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public boolean isPublicDocument() { return publicDocument; }
    public void setPublicDocument(boolean publicDocument) { this.publicDocument = publicDocument; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
}
