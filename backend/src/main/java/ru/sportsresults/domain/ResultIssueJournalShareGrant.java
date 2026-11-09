package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_issue_journal_share_grants")
public class ResultIssueJournalShareGrant {

    @Id
    private UUID id;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true, updatable = false)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_series_id", nullable = false, updatable = false)
    private EventSeries eventSeries;

    @Column(name = "sheet_year", nullable = false, updatable = false)
    private int sheetYear;

    @Column(name = "city_scope", length = 255, updatable = false)
    private String cityScope;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private JournalSharePermission permission;

    @Column(name = "can_sync_now", nullable = false, updatable = false)
    private boolean canSyncNow;

    @Column(length = 160, updatable = false)
    private String label;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 160, updatable = false)
    private String createdBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by", length = 160)
    private String revokedBy;

    @Column(name = "last_accessed_at")
    private Instant lastAccessedAt;

    @Column(name = "access_count", nullable = false)
    private long accessCount;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public EventSeries getEventSeries() { return eventSeries; }
    public void setEventSeries(EventSeries eventSeries) { this.eventSeries = eventSeries; }
    public int getSheetYear() { return sheetYear; }
    public void setSheetYear(int sheetYear) { this.sheetYear = sheetYear; }
    public String getCityScope() { return cityScope; }
    public void setCityScope(String cityScope) { this.cityScope = cityScope; }
    public JournalSharePermission getPermission() { return permission; }
    public void setPermission(JournalSharePermission permission) { this.permission = permission; }
    public boolean isCanSyncNow() { return canSyncNow; }
    public void setCanSyncNow(boolean canSyncNow) { this.canSyncNow = canSyncNow; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public void setLastAccessedAt(Instant lastAccessedAt) { this.lastAccessedAt = lastAccessedAt; }
    public long getAccessCount() { return accessCount; }
    public void setAccessCount(long accessCount) { this.accessCount = accessCount; }
}
