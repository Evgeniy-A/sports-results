package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Entity
@Table(name = "checkpoints", uniqueConstraints = {
        @UniqueConstraint(name = "uk_checkpoints_race_code", columnNames = {"race_id", "code"}),
        @UniqueConstraint(name = "uk_checkpoints_race_sequence", columnNames = {"race_id", "sequence_number"})
})
public class Checkpoint extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String code;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @Positive
    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @PositiveOrZero
    @Column(name = "distance_meters", precision = 12, scale = 3)
    private BigDecimal distanceMeters;

    public Race getRace() {
        return race;
    }

    public void setRace(Race race) {
        this.race = race;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public BigDecimal getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(BigDecimal distanceMeters) {
        this.distanceMeters = distanceMeters;
    }
}
