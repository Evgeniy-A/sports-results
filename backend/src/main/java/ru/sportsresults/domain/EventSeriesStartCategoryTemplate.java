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

@Entity
@Table(name = "event_series_start_category_templates", uniqueConstraints =
        @UniqueConstraint(name = "uk_event_series_start_category_templates_source",
                columnNames = {"template_start_id", "source_name"}))
public class EventSeriesStartCategoryTemplate extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_start_id", nullable = false)
    private EventSeriesStartTemplate templateStart;

    @NotBlank
    @Size(max = 255)
    @Column(name = "source_name", nullable = false, length = 255)
    private String sourceName;

    @NotBlank
    @Size(max = 255)
    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @PositiveOrZero
    @Column(name = "min_age")
    private Integer minAge;

    @PositiveOrZero
    @Column(name = "max_age")
    private Integer maxAge;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private CategoryGender gender;

    @Column(nullable = false)
    private boolean enabled = true;

    public EventSeriesStartTemplate getTemplateStart() { return templateStart; }
    public void setTemplateStart(EventSeriesStartTemplate templateStart) { this.templateStart = templateStart; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
    public Integer getMinAge() { return minAge; }
    public void setMinAge(Integer minAge) { this.minAge = minAge; }
    public Integer getMaxAge() { return maxAge; }
    public void setMaxAge(Integer maxAge) { this.maxAge = maxAge; }
    public CategoryGender getGender() { return gender; }
    public void setGender(CategoryGender gender) { this.gender = gender; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
