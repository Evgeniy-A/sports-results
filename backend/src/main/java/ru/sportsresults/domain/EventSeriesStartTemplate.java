package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Entity
@Table(name = "event_series_start_templates")
public class EventSeriesStartTemplate extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_series_id", nullable = false)
    private EventSeries eventSeries;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @PositiveOrZero
    @Column(name = "distance_meters", precision = 12, scale = 3)
    private BigDecimal distanceMeters;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "public_visible", nullable = false)
    private boolean publicVisible = true;

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

    public BigDecimal getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(BigDecimal distanceMeters) {
        this.distanceMeters = distanceMeters;
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
}
