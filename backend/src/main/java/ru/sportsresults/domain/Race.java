package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "races", uniqueConstraints = {
        @UniqueConstraint(name = "uk_races_event_source_code", columnNames = {"event_id", "source_code"}),
        @UniqueConstraint(name = "uk_races_event_slug", columnNames = {"event_id", "slug"})
})
public class Race extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @NotBlank
    @Size(max = 255)
    @Column(name = "source_code", nullable = false, length = 255)
    private String sourceCode;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String slug;

    @PositiveOrZero
    @Column(name = "distance_meters", precision = 12, scale = 3)
    private BigDecimal distanceMeters;

    @Column(name = "starts_at")
    private Instant startsAt;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "public_ranking_basis", nullable = false, length = 32)
    private RankingBasis publicRankingBasis = RankingBasis.CHIP_TIME;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "public_visible", nullable = false)
    private boolean publicVisible = true;

    @Column(name = "result_recalculation_required", nullable = false)
    private boolean resultRecalculationRequired;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "results_publication_status", nullable = false, length = 32)
    private ResultsPublicationStatus resultsPublicationStatus = ResultsPublicationStatus.DRAFT;

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
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

    public BigDecimal getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(BigDecimal distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public RankingBasis getPublicRankingBasis() {
        return publicRankingBasis;
    }

    public void setPublicRankingBasis(RankingBasis publicRankingBasis) {
        this.publicRankingBasis = publicRankingBasis;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isPublicVisible() {
        return publicVisible;
    }

    public void setPublicVisible(boolean publicVisible) {
        this.publicVisible = publicVisible;
    }

    public boolean isResultRecalculationRequired() {
        return resultRecalculationRequired;
    }

    public void setResultRecalculationRequired(boolean resultRecalculationRequired) {
        this.resultRecalculationRequired = resultRecalculationRequired;
    }

    public ResultsPublicationStatus getResultsPublicationStatus() {
        return resultsPublicationStatus;
    }

    public void setResultsPublicationStatus(ResultsPublicationStatus resultsPublicationStatus) {
        this.resultsPublicationStatus = resultsPublicationStatus;
    }
}
