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
@Table(name = "race_result_publication_history")
public class RaceResultPublicationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 32)
    private ResultsPublicationStatus fromStatus;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private ResultsPublicationStatus toStatus;

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String actor;

    @Size(max = 1000)
    @Column(length = 1000)
    private String reason;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Race getRace() {
        return race;
    }

    public void setRace(Race race) {
        this.race = race;
    }

    public ResultsPublicationStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(ResultsPublicationStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public ResultsPublicationStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(ResultsPublicationStatus toStatus) {
        this.toStatus = toStatus;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
