package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_issue_share_batches")
public class ResultIssueShareBatch {

    @Id
    private UUID id;

    @NotBlank
    @Size(max = 160)
    @Column(name = "created_by", nullable = false, length = 160, updatable = false)
    private String createdBy;

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

    @PositiveOrZero
    @Column(name = "matched_issue_count", nullable = false, updatable = false)
    private long matchedIssueCount;

    @PositiveOrZero
    @Column(name = "grant_count", nullable = false, updatable = false)
    private long grantCount;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public long getMatchedIssueCount() { return matchedIssueCount; }
    public void setMatchedIssueCount(long matchedIssueCount) { this.matchedIssueCount = matchedIssueCount; }
    public long getGrantCount() { return grantCount; }
    public void setGrantCount(long grantCount) { this.grantCount = grantCount; }
}
