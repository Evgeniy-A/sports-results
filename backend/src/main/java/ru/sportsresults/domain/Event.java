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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

@Entity
@Table(name = "events")
public class Event extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_series_id", nullable = false)
    private EventSeries eventSeries;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160, unique = true)
    private String slug;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Size(max = 255)
    @Column(length = 255)
    private String location;

    @NotBlank
    @Size(max = 64)
    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "Europe/Moscow";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 32)
    private EventPublicationStatus publicationStatus = EventPublicationStatus.DRAFT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "results_publication_status", nullable = false, length = 32)
    private ResultsPublicationStatus resultsPublicationStatus = ResultsPublicationStatus.DRAFT;

    @Column(name = "result_inquiry_enabled", nullable = false)
    private boolean resultInquiryEnabled;

    @Positive
    @Column(name = "result_inquiry_window_days")
    private Integer resultInquiryWindowDays;

    @Email
    @Size(max = 320)
    @Column(name = "result_inquiry_email", length = 320)
    private String resultInquiryEmail;

    @PositiveOrZero
    @Column(name = "result_data_revision", nullable = false)
    private long resultDataRevision;

    public EventSeries getEventSeries() {
        return eventSeries;
    }

    public void setEventSeries(EventSeries eventSeries) {
        this.eventSeries = eventSeries;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getTimeZone() { return timeZone; }

    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }

    public EventPublicationStatus getPublicationStatus() {
        return publicationStatus;
    }

    public void setPublicationStatus(EventPublicationStatus publicationStatus) {
        this.publicationStatus = publicationStatus;
    }

    public ResultsPublicationStatus getResultsPublicationStatus() { return resultsPublicationStatus; }

    public void setResultsPublicationStatus(ResultsPublicationStatus resultsPublicationStatus) {
        this.resultsPublicationStatus = resultsPublicationStatus;
    }

    public boolean isResultInquiryEnabled() { return resultInquiryEnabled; }

    public void setResultInquiryEnabled(boolean resultInquiryEnabled) {
        this.resultInquiryEnabled = resultInquiryEnabled;
    }

    public Integer getResultInquiryWindowDays() { return resultInquiryWindowDays; }

    public void setResultInquiryWindowDays(Integer resultInquiryWindowDays) {
        this.resultInquiryWindowDays = resultInquiryWindowDays;
    }

    public String getResultInquiryEmail() { return resultInquiryEmail; }

    public void setResultInquiryEmail(String resultInquiryEmail) {
        this.resultInquiryEmail = resultInquiryEmail;
    }

    public long getResultDataRevision() { return resultDataRevision; }

    public void setResultDataRevision(long resultDataRevision) { this.resultDataRevision = resultDataRevision; }
}
