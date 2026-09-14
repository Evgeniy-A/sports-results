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
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "import_batches")
public class ImportBatch extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id")
    private Race race;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16)
    private ImportScopeType scopeType = ImportScopeType.EVENT;

    @NotBlank
    @Size(max = 255)
    @Column(name = "source_filename", nullable = false, length = 255)
    private String sourceFilename;

    @NotBlank
    @Size(min = 64, max = 64)
    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImportBatchStatus status = ImportBatchStatus.PENDING;

    @PositiveOrZero
    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @PositiveOrZero
    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @PositiveOrZero
    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows;

    @PositiveOrZero
    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_summary", columnDefinition = "jsonb")
    private String errorSummary;

    @NotNull
    @Column(name = "started_at", nullable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "finished_at")
    private Instant finishedAt;

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public Race getRace() {
        return race;
    }

    public void setRace(Race race) {
        this.race = race;
    }

    public ImportScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(ImportScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public String getSourceFilename() {
        return sourceFilename;
    }

    public void setSourceFilename(String sourceFilename) {
        this.sourceFilename = sourceFilename;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public void setFileSha256(String fileSha256) {
        this.fileSha256 = fileSha256;
    }

    public ImportBatchStatus getStatus() {
        return status;
    }

    public void setStatus(ImportBatchStatus status) {
        this.status = status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public void setImportedRows(int importedRows) {
        this.importedRows = importedRows;
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public void setSkippedRows(int skippedRows) {
        this.skippedRows = skippedRows;
    }

    public int getFailedRows() {
        return failedRows;
    }

    public void setFailedRows(int failedRows) {
        this.failedRows = failedRows;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
