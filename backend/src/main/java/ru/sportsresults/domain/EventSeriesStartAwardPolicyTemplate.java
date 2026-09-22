package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
@Table(name = "event_series_start_award_policy_templates")
public class EventSeriesStartAwardPolicyTemplate extends BaseEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_start_id", nullable = false, unique = true)
    private EventSeriesStartTemplate templateStart;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "ranking_basis", nullable = false, length = 32)
    private RankingBasis rankingBasis;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_standing_mode", nullable = false, length = 32)
    private PrimaryStandingMode primaryStandingMode;

    @PositiveOrZero
    @Column(name = "absolute_prize_places", nullable = false)
    private int absolutePrizePlaces;

    @Column(name = "category_enabled", nullable = false)
    private boolean categoryEnabled;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "age_calculation_mode", nullable = false, length = 32)
    private AgeCalculationMode ageCalculationMode;

    @PositiveOrZero
    @Column(name = "category_prize_places", nullable = false)
    private int categoryPrizePlaces;

    @Column(name = "exclude_absolute_winners_from_category", nullable = false)
    private boolean excludeAbsoluteWinnersFromCategory;

    public EventSeriesStartTemplate getTemplateStart() {
        return templateStart;
    }

    public void setTemplateStart(EventSeriesStartTemplate templateStart) {
        this.templateStart = templateStart;
    }

    public RankingBasis getRankingBasis() {
        return rankingBasis;
    }

    public void setRankingBasis(RankingBasis rankingBasis) {
        this.rankingBasis = rankingBasis;
    }

    public PrimaryStandingMode getPrimaryStandingMode() {
        return primaryStandingMode;
    }

    public void setPrimaryStandingMode(PrimaryStandingMode primaryStandingMode) {
        this.primaryStandingMode = primaryStandingMode;
    }

    public int getAbsolutePrizePlaces() {
        return absolutePrizePlaces;
    }

    public void setAbsolutePrizePlaces(int absolutePrizePlaces) {
        this.absolutePrizePlaces = absolutePrizePlaces;
    }

    public boolean isCategoryEnabled() {
        return categoryEnabled;
    }

    public void setCategoryEnabled(boolean categoryEnabled) {
        this.categoryEnabled = categoryEnabled;
    }

    public AgeCalculationMode getAgeCalculationMode() {
        return ageCalculationMode;
    }

    public void setAgeCalculationMode(AgeCalculationMode ageCalculationMode) {
        this.ageCalculationMode = ageCalculationMode;
    }

    public int getCategoryPrizePlaces() {
        return categoryPrizePlaces;
    }

    public void setCategoryPrizePlaces(int categoryPrizePlaces) {
        this.categoryPrizePlaces = categoryPrizePlaces;
    }

    public boolean isExcludeAbsoluteWinnersFromCategory() {
        return excludeAbsoluteWinnersFromCategory;
    }

    public void setExcludeAbsoluteWinnersFromCategory(boolean value) {
        this.excludeAbsoluteWinnersFromCategory = value;
    }
}
