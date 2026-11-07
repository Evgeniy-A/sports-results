package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "result_issue_work_items")
public class ResultIssueWorkItem extends BaseEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_request_id", nullable = false, unique = true)
    private ResultIssueRequest issueRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id")
    private Result result;

    @Size(max = 64)
    @Column(name = "base_result_status", length = 64)
    private String baseResultStatus;

    @Column(name = "base_gun_time_ms")
    private Duration baseGunTime;

    @Column(name = "base_chip_time_ms")
    private Duration baseChipTime;

    @Size(max = 64)
    @Column(name = "desired_result_status", length = 64)
    private String desiredResultStatus;

    @Column(name = "desired_gun_time_ms")
    private Duration desiredGunTime;

    @Column(name = "desired_chip_time_ms")
    private Duration desiredChipTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_user_id")
    private AdminUser assignee;

    @Size(max = 4000)
    @Column(name = "internal_comment", columnDefinition = "text")
    private String internalComment;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false, length = 16)
    private ResultIssueWorkSyncStatus syncStatus = ResultIssueWorkSyncStatus.PENDING;

    @Size(max = 100)
    @Column(name = "sync_error_code", length = 100)
    private String syncErrorCode;

    @Version
    @Column(nullable = false)
    private long version;

    @NotBlank
    @Size(max = 160)
    @Column(name = "updated_by", nullable = false, length = 160)
    private String updatedBy;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Size(max = 160)
    @Column(name = "synced_by", length = 160)
    private String syncedBy;

    public ResultIssueRequest getIssueRequest() { return issueRequest; }
    public void setIssueRequest(ResultIssueRequest issueRequest) { this.issueRequest = issueRequest; }
    public Result getResult() { return result; }
    public void setResult(Result result) { this.result = result; }
    public String getBaseResultStatus() { return baseResultStatus; }
    public void setBaseResultStatus(String value) { this.baseResultStatus = value; }
    public Duration getBaseGunTime() { return baseGunTime; }
    public void setBaseGunTime(Duration value) { this.baseGunTime = value; }
    public Duration getBaseChipTime() { return baseChipTime; }
    public void setBaseChipTime(Duration value) { this.baseChipTime = value; }
    public String getDesiredResultStatus() { return desiredResultStatus; }
    public void setDesiredResultStatus(String value) { this.desiredResultStatus = value; }
    public Duration getDesiredGunTime() { return desiredGunTime; }
    public void setDesiredGunTime(Duration value) { this.desiredGunTime = value; }
    public Duration getDesiredChipTime() { return desiredChipTime; }
    public void setDesiredChipTime(Duration value) { this.desiredChipTime = value; }
    public AdminUser getAssignee() { return assignee; }
    public void setAssignee(AdminUser assignee) { this.assignee = assignee; }
    public String getInternalComment() { return internalComment; }
    public void setInternalComment(String value) { this.internalComment = value; }
    public ResultIssueWorkSyncStatus getSyncStatus() { return syncStatus; }
    public void setSyncStatus(ResultIssueWorkSyncStatus value) { this.syncStatus = value; }
    public String getSyncErrorCode() { return syncErrorCode; }
    public void setSyncErrorCode(String value) { this.syncErrorCode = value; }
    public long getVersion() { return version; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String value) { this.updatedBy = value; }
    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant value) { this.syncedAt = value; }
    public String getSyncedBy() { return syncedBy; }
    public void setSyncedBy(String value) { this.syncedBy = value; }
}
