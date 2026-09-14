package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "result_issue_requests")
public class ResultIssueRequest extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false)
    private Registration registration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id")
    private Result result;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false, length = 32)
    private ResultIssueType issueType;

    @Enumerated(EnumType.STRING)
    @Column(name = "correction_reason", length = 32)
    private ResultCorrectionReason correctionReason;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResultIssueStatus status = ResultIssueStatus.NEW;

    @NotBlank
    @Email
    @Size(max = 320)
    @Column(name = "contact_email", nullable = false, length = 320)
    private String contactEmail;

    @NotBlank
    @Size(max = 4000)
    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "claimed_gun_time_ms")
    private Duration claimedGunTime;

    @Column(name = "claimed_chip_time_ms")
    private Duration claimedChipTime;

    @Column(name = "estimated_start_at")
    private Instant estimatedStartAt;

    @Column(name = "estimated_finish_at")
    private Instant estimatedFinishAt;

    @Column(name = "observed_gun_time_ms", updatable = false)
    private Duration observedGunTime;

    @Column(name = "observed_chip_time_ms", updatable = false)
    private Duration observedChipTime;

    @Size(max = 64)
    @Column(name = "observed_result_status", length = 64, updatable = false)
    private String observedResultStatus;

    @Column(name = "queue_archived_at")
    private Instant queueArchivedAt;

    @Size(max = 160)
    @Column(name = "queue_archived_by", length = 160)
    private String queueArchivedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_archive_reason", length = 32)
    private ResultIssueArchiveReason queueArchiveReason;

    @Column(name = "queue_archived_import_operation_id")
    private UUID queueArchivedImportOperationId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_origin", nullable = false, length = 40, updatable = false)
    private ResultIssueSnapshotOrigin snapshotOrigin;

    @Size(max = 255)
    @Column(name = "snapshot_event_name", length = 255, updatable = false)
    private String snapshotEventName;

    @Size(max = 255)
    @Column(name = "snapshot_event_location", length = 255, updatable = false)
    private String snapshotEventLocation;

    @Column(name = "snapshot_event_starts_at", updatable = false)
    private Instant snapshotEventStartsAt;

    @Column(name = "snapshot_sport_format_id", updatable = false)
    private Long snapshotSportFormatId;

    @Size(max = 255)
    @Column(name = "snapshot_sport_format_name", length = 255, updatable = false)
    private String snapshotSportFormatName;

    @Size(max = 100)
    @Column(name = "snapshot_sport_format_code", length = 100, updatable = false)
    private String snapshotSportFormatCode;

    @Column(name = "snapshot_race_id", updatable = false)
    private Long snapshotRaceId;

    @Size(max = 255)
    @Column(name = "snapshot_race_name", length = 255, updatable = false)
    private String snapshotRaceName;

    @Size(max = 255)
    @Column(name = "snapshot_race_code", length = 255, updatable = false)
    private String snapshotRaceCode;

    @Column(name = "snapshot_race_distance_meters", precision = 12, scale = 3, updatable = false)
    private BigDecimal snapshotRaceDistanceMeters;

    @Size(max = 64)
    @Column(name = "snapshot_bib", length = 64, updatable = false)
    private String snapshotBib;

    @Size(max = 320)
    @Column(name = "snapshot_display_name", length = 320, updatable = false)
    private String snapshotDisplayName;

    @Size(max = 255)
    @Column(name = "snapshot_effective_category_name", length = 255, updatable = false)
    private String snapshotEffectiveCategoryName;

    @Size(max = 255)
    @Column(name = "snapshot_source_category", length = 255, updatable = false)
    private String snapshotSourceCategory;

    @Column(name = "snapshot_category_publicly_enabled", updatable = false)
    private Boolean snapshotCategoryPubliclyEnabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_ranking", columnDefinition = "jsonb", updatable = false)
    private String snapshotRanking;

    @Column(name = "snapshot_import_batch_id", updatable = false)
    private Long snapshotImportBatchId;

    @Column(name = "snapshot_source_row_number", updatable = false)
    private Integer snapshotSourceRowNumber;

    @Size(max = 64)
    @Column(name = "attachment_upload_token_hash", length = 64)
    private String attachmentUploadTokenHash;

    @Column(name = "attachment_upload_token_expires_at")
    private Instant attachmentUploadTokenExpiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public Registration getRegistration() { return registration; }
    public void setRegistration(Registration registration) { this.registration = registration; }
    public Result getResult() { return result; }
    public void setResult(Result result) { this.result = result; }
    public ResultIssueType getIssueType() { return issueType; }
    public void setIssueType(ResultIssueType issueType) { this.issueType = issueType; }
    public ResultCorrectionReason getCorrectionReason() { return correctionReason; }
    public void setCorrectionReason(ResultCorrectionReason correctionReason) { this.correctionReason = correctionReason; }
    public ResultIssueStatus getStatus() { return status; }
    public void setStatus(ResultIssueStatus status) { this.status = status; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Duration getClaimedGunTime() { return claimedGunTime; }
    public void setClaimedGunTime(Duration claimedGunTime) { this.claimedGunTime = claimedGunTime; }
    public Duration getClaimedChipTime() { return claimedChipTime; }
    public void setClaimedChipTime(Duration claimedChipTime) { this.claimedChipTime = claimedChipTime; }
    public Instant getEstimatedStartAt() { return estimatedStartAt; }
    public void setEstimatedStartAt(Instant estimatedStartAt) { this.estimatedStartAt = estimatedStartAt; }
    public Instant getEstimatedFinishAt() { return estimatedFinishAt; }
    public void setEstimatedFinishAt(Instant estimatedFinishAt) { this.estimatedFinishAt = estimatedFinishAt; }
    public Duration getObservedGunTime() { return observedGunTime; }
    public void setObservedGunTime(Duration observedGunTime) { this.observedGunTime = observedGunTime; }
    public Duration getObservedChipTime() { return observedChipTime; }
    public void setObservedChipTime(Duration observedChipTime) { this.observedChipTime = observedChipTime; }
    public String getObservedResultStatus() { return observedResultStatus; }
    public void setObservedResultStatus(String observedResultStatus) { this.observedResultStatus = observedResultStatus; }
    public Instant getQueueArchivedAt() { return queueArchivedAt; }
    public void setQueueArchivedAt(Instant queueArchivedAt) { this.queueArchivedAt = queueArchivedAt; }
    public String getQueueArchivedBy() { return queueArchivedBy; }
    public void setQueueArchivedBy(String queueArchivedBy) { this.queueArchivedBy = queueArchivedBy; }
    public ResultIssueArchiveReason getQueueArchiveReason() { return queueArchiveReason; }
    public void setQueueArchiveReason(ResultIssueArchiveReason queueArchiveReason) {
        this.queueArchiveReason = queueArchiveReason;
    }
    public UUID getQueueArchivedImportOperationId() { return queueArchivedImportOperationId; }
    public void setQueueArchivedImportOperationId(UUID value) { this.queueArchivedImportOperationId = value; }
    public ResultIssueSnapshotOrigin getSnapshotOrigin() { return snapshotOrigin; }
    public void setSnapshotOrigin(ResultIssueSnapshotOrigin snapshotOrigin) { this.snapshotOrigin = snapshotOrigin; }
    public String getSnapshotEventName() { return snapshotEventName; }
    public void setSnapshotEventName(String value) { this.snapshotEventName = value; }
    public String getSnapshotEventLocation() { return snapshotEventLocation; }
    public void setSnapshotEventLocation(String value) { this.snapshotEventLocation = value; }
    public Instant getSnapshotEventStartsAt() { return snapshotEventStartsAt; }
    public void setSnapshotEventStartsAt(Instant value) { this.snapshotEventStartsAt = value; }
    public Long getSnapshotSportFormatId() { return snapshotSportFormatId; }
    public void setSnapshotSportFormatId(Long value) { this.snapshotSportFormatId = value; }
    public String getSnapshotSportFormatName() { return snapshotSportFormatName; }
    public void setSnapshotSportFormatName(String value) { this.snapshotSportFormatName = value; }
    public String getSnapshotSportFormatCode() { return snapshotSportFormatCode; }
    public void setSnapshotSportFormatCode(String value) { this.snapshotSportFormatCode = value; }
    public Long getSnapshotRaceId() { return snapshotRaceId; }
    public void setSnapshotRaceId(Long value) { this.snapshotRaceId = value; }
    public String getSnapshotRaceName() { return snapshotRaceName; }
    public void setSnapshotRaceName(String value) { this.snapshotRaceName = value; }
    public String getSnapshotRaceCode() { return snapshotRaceCode; }
    public void setSnapshotRaceCode(String value) { this.snapshotRaceCode = value; }
    public BigDecimal getSnapshotRaceDistanceMeters() { return snapshotRaceDistanceMeters; }
    public void setSnapshotRaceDistanceMeters(BigDecimal value) { this.snapshotRaceDistanceMeters = value; }
    public String getSnapshotBib() { return snapshotBib; }
    public void setSnapshotBib(String value) { this.snapshotBib = value; }
    public String getSnapshotDisplayName() { return snapshotDisplayName; }
    public void setSnapshotDisplayName(String value) { this.snapshotDisplayName = value; }
    public String getSnapshotEffectiveCategoryName() { return snapshotEffectiveCategoryName; }
    public void setSnapshotEffectiveCategoryName(String value) { this.snapshotEffectiveCategoryName = value; }
    public String getSnapshotSourceCategory() { return snapshotSourceCategory; }
    public void setSnapshotSourceCategory(String value) { this.snapshotSourceCategory = value; }
    public Boolean getSnapshotCategoryPubliclyEnabled() { return snapshotCategoryPubliclyEnabled; }
    public void setSnapshotCategoryPubliclyEnabled(Boolean value) { this.snapshotCategoryPubliclyEnabled = value; }
    public String getSnapshotRanking() { return snapshotRanking; }
    public void setSnapshotRanking(String value) { this.snapshotRanking = value; }
    public Long getSnapshotImportBatchId() { return snapshotImportBatchId; }
    public void setSnapshotImportBatchId(Long value) { this.snapshotImportBatchId = value; }
    public Integer getSnapshotSourceRowNumber() { return snapshotSourceRowNumber; }
    public void setSnapshotSourceRowNumber(Integer value) { this.snapshotSourceRowNumber = value; }
    public String getAttachmentUploadTokenHash() { return attachmentUploadTokenHash; }
    public void setAttachmentUploadTokenHash(String value) { this.attachmentUploadTokenHash = value; }
    public Instant getAttachmentUploadTokenExpiresAt() { return attachmentUploadTokenExpiresAt; }
    public void setAttachmentUploadTokenExpiresAt(Instant value) { this.attachmentUploadTokenExpiresAt = value; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
