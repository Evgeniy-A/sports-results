package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "result_recalculation_operations")
public class ResultRecalculationOperation {

    @Id
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "result_recalculation_operation_races",
            joinColumns = @JoinColumn(name = "operation_id"),
            inverseJoinColumns = @JoinColumn(name = "race_id")
    )
    private Set<Race> races = new LinkedHashSet<>();

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResultRecalculationOperationStatus status = ResultRecalculationOperationStatus.PREVIEWED;

    @PositiveOrZero
    @Column(name = "base_revision", nullable = false)
    private long baseRevision;

    @NotBlank
    @Size(min = 64, max = 64)
    @Column(name = "configuration_digest", nullable = false, length = 64)
    private String configurationDigest;

    @NotBlank
    @Size(min = 64, max = 64)
    @Column(name = "plan_digest", nullable = false, length = 64)
    private String planDigest;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_summary", nullable = false, columnDefinition = "jsonb")
    private String previewSummary;

    @PositiveOrZero
    @Column(name = "current_count", nullable = false)
    private int currentCount;

    @PositiveOrZero
    @Column(name = "changed_count", nullable = false)
    private int changedCount;

    @PositiveOrZero
    @Column(name = "blocking_count", nullable = false)
    private int blockingCount;

    @NotBlank
    @Size(max = 160)
    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Size(max = 160)
    @Column(name = "applied_by", length = 160)
    private String appliedBy;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @PositiveOrZero
    @Column(name = "new_revision")
    private Long newRevision;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public Set<Race> getRaces() { return races; }
    public void setRaces(Set<Race> races) { this.races = new LinkedHashSet<>(races); }
    public ResultRecalculationOperationStatus getStatus() { return status; }
    public void setStatus(ResultRecalculationOperationStatus status) { this.status = status; }
    public long getBaseRevision() { return baseRevision; }
    public void setBaseRevision(long baseRevision) { this.baseRevision = baseRevision; }
    public String getConfigurationDigest() { return configurationDigest; }
    public void setConfigurationDigest(String configurationDigest) { this.configurationDigest = configurationDigest; }
    public String getPlanDigest() { return planDigest; }
    public void setPlanDigest(String planDigest) { this.planDigest = planDigest; }
    public String getPreviewSummary() { return previewSummary; }
    public void setPreviewSummary(String previewSummary) { this.previewSummary = previewSummary; }
    public int getCurrentCount() { return currentCount; }
    public void setCurrentCount(int currentCount) { this.currentCount = currentCount; }
    public int getChangedCount() { return changedCount; }
    public void setChangedCount(int changedCount) { this.changedCount = changedCount; }
    public int getBlockingCount() { return blockingCount; }
    public void setBlockingCount(int blockingCount) { this.blockingCount = blockingCount; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getAppliedBy() { return appliedBy; }
    public void setAppliedBy(String appliedBy) { this.appliedBy = appliedBy; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public Long getNewRevision() { return newRevision; }
    public void setNewRevision(Long newRevision) { this.newRevision = newRevision; }
}
