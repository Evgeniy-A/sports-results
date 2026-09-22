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
import jakarta.persistence.OneToOne;
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
@Table(name = "import_operations")
public class ImportOperation {

    @Id
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "import_operation_races",
            joinColumns = @JoinColumn(name = "operation_id"),
            inverseJoinColumns = @JoinColumn(name = "race_id")
    )
    private Set<Race> races = new LinkedHashSet<>();

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "operation_mode", nullable = false, length = 32)
    private ImportOperationMode mode;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImportOperationStatus status = ImportOperationStatus.PREVIEWED;

    @NotBlank
    @Size(max = 255)
    @Column(name = "source_filename", nullable = false, length = 255)
    private String sourceFilename;

    @NotBlank
    @Size(min = 64, max = 64)
    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @PositiveOrZero
    @Column(name = "base_revision", nullable = false)
    private long baseRevision;

    @NotBlank
    @Size(min = 64, max = 64)
    @Column(name = "plan_digest", nullable = false, length = 64)
    private String planDigest;

    @NotBlank
    @Size(max = 160)
    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview_summary", nullable = false, columnDefinition = "jsonb")
    private String previewSummary;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_config", nullable = false, columnDefinition = "jsonb")
    private String inputConfig = "{}";

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @NotNull
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "import_batch_id", unique = true)
    private ImportBatch importBatch;

    @Size(max = 160)
    @Column(name = "applied_by", length = 160)
    private String appliedBy;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @PositiveOrZero
    @Column(name = "inserted_count")
    private Integer insertedCount;

    @PositiveOrZero
    @Column(name = "existing_skipped_count")
    private Integer existingSkippedCount;

    @PositiveOrZero
    @Column(name = "out_of_scope_count")
    private Integer outOfScopeCount;

    @PositiveOrZero
    @Column(name = "new_revision")
    private Long newRevision;

    @PositiveOrZero
    @Column(name = "updated_count", nullable = false)
    private int updatedCount;

    @PositiveOrZero
    @Column(name = "result_created_count", nullable = false)
    private int resultCreatedCount;

    @PositiveOrZero
    @Column(name = "new_skipped_count", nullable = false)
    private int newSkippedCount;

    @PositiveOrZero
    @Column(name = "unchanged_count", nullable = false)
    private int unchangedCount;

    @PositiveOrZero
    @Column(name = "retired_count", nullable = false)
    private int retiredCount;

    @PositiveOrZero
    @Column(name = "archived_issue_count", nullable = false)
    private int archivedIssueCount;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public Set<Race> getRaces() { return races; }
    public void setRaces(Set<Race> races) { this.races = new LinkedHashSet<>(races); }
    public ImportOperationMode getMode() { return mode; }
    public void setMode(ImportOperationMode mode) { this.mode = mode; }
    public ImportOperationStatus getStatus() { return status; }
    public void setStatus(ImportOperationStatus status) { this.status = status; }
    public String getSourceFilename() { return sourceFilename; }
    public void setSourceFilename(String sourceFilename) { this.sourceFilename = sourceFilename; }
    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }
    public long getBaseRevision() { return baseRevision; }
    public void setBaseRevision(long baseRevision) { this.baseRevision = baseRevision; }
    public String getPlanDigest() { return planDigest; }
    public void setPlanDigest(String planDigest) { this.planDigest = planDigest; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getPreviewSummary() { return previewSummary; }
    public void setPreviewSummary(String previewSummary) { this.previewSummary = previewSummary; }
    public String getInputConfig() { return inputConfig; }
    public void setInputConfig(String inputConfig) { this.inputConfig = inputConfig; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public ImportBatch getImportBatch() { return importBatch; }
    public void setImportBatch(ImportBatch importBatch) { this.importBatch = importBatch; }
    public String getAppliedBy() { return appliedBy; }
    public void setAppliedBy(String appliedBy) { this.appliedBy = appliedBy; }
    public Instant getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public Integer getInsertedCount() { return insertedCount; }
    public void setInsertedCount(Integer insertedCount) { this.insertedCount = insertedCount; }
    public Integer getExistingSkippedCount() { return existingSkippedCount; }
    public void setExistingSkippedCount(Integer existingSkippedCount) { this.existingSkippedCount = existingSkippedCount; }
    public Integer getOutOfScopeCount() { return outOfScopeCount; }
    public void setOutOfScopeCount(Integer outOfScopeCount) { this.outOfScopeCount = outOfScopeCount; }
    public Long getNewRevision() { return newRevision; }
    public void setNewRevision(Long newRevision) { this.newRevision = newRevision; }
    public int getUpdatedCount() { return updatedCount; }
    public void setUpdatedCount(int updatedCount) { this.updatedCount = updatedCount; }
    public int getResultCreatedCount() { return resultCreatedCount; }
    public void setResultCreatedCount(int resultCreatedCount) { this.resultCreatedCount = resultCreatedCount; }
    public int getNewSkippedCount() { return newSkippedCount; }
    public void setNewSkippedCount(int newSkippedCount) { this.newSkippedCount = newSkippedCount; }
    public int getUnchangedCount() { return unchangedCount; }
    public void setUnchangedCount(int unchangedCount) { this.unchangedCount = unchangedCount; }
    public int getRetiredCount() { return retiredCount; }
    public void setRetiredCount(int retiredCount) { this.retiredCount = retiredCount; }
    public int getArchivedIssueCount() { return archivedIssueCount; }
    public void setArchivedIssueCount(int archivedIssueCount) { this.archivedIssueCount = archivedIssueCount; }
}
