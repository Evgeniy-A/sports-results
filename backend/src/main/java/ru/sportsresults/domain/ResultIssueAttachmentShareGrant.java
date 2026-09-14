package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_issue_attachment_share_grants")
public class ResultIssueAttachmentShareGrant {

    @Id
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "batch_id", nullable = false, updatable = false)
    private ResultIssueShareBatch batch;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attachment_id", nullable = false, updatable = false)
    private ResultIssueAttachment attachment;

    @NotBlank
    @Size(min = 64, max = 64)
    @Column(name = "token_hash", nullable = false, length = 64, unique = true, updatable = false)
    private String tokenHash;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Size(max = 160)
    @Column(name = "revoked_by", length = 160)
    private String revokedBy;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @PositiveOrZero
    @Column(name = "access_count", nullable = false)
    private long accessCount;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public ResultIssueShareBatch getBatch() { return batch; }
    public void setBatch(ResultIssueShareBatch batch) { this.batch = batch; }
    public ResultIssueAttachment getAttachment() { return attachment; }
    public void setAttachment(ResultIssueAttachment attachment) { this.attachment = attachment; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
    public long getAccessCount() { return accessCount; }
    public void setAccessCount(long accessCount) { this.accessCount = accessCount; }
}
