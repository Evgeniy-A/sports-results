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
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

@Entity
@Table(name = "start_clusters", uniqueConstraints =
        @UniqueConstraint(name = "uk_start_clusters_race_display_name", columnNames = {"race_id", "display_name"}))
public class StartCluster extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @Size(max = 100)
    @Column(length = 100)
    private String code;

    @Size(max = 255)
    @Column(name = "source_name", length = 255)
    private String sourceName;

    @NotBlank
    @Size(max = 255)
    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @PositiveOrZero
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    public Race getRace() { return race; }
    public void setRace(Race race) { this.race = race; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
}
