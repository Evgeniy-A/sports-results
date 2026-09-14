package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Entity
@Table(name = "result_issue_history")
public class ResultIssueHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_request_id", nullable = false, updatable = false)
    private ResultIssueRequest issueRequest;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private ResultIssueHistoryAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32, updatable = false)
    private ResultIssueStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 32, updatable = false)
    private ResultIssueStatus toStatus;

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160, updatable = false)
    private String actor;

    @Size(max = 1000)
    @Column(length = 1000, updatable = false)
    private String reason;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public ResultIssueRequest getIssueRequest() { return issueRequest; }
    public void setIssueRequest(ResultIssueRequest issueRequest) { this.issueRequest = issueRequest; }
    public ResultIssueHistoryAction getAction() { return action; }
    public void setAction(ResultIssueHistoryAction action) { this.action = action; }
    public ResultIssueStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(ResultIssueStatus fromStatus) { this.fromStatus = fromStatus; }
    public ResultIssueStatus getToStatus() { return toStatus; }
    public void setToStatus(ResultIssueStatus toStatus) { this.toStatus = toStatus; }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
