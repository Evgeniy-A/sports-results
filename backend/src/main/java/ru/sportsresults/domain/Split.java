package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Duration;

@Entity
@Table(name = "splits", uniqueConstraints =
        @UniqueConstraint(name = "uk_splits_result_checkpoint", columnNames = {"result_id", "checkpoint_id"}))
public class Split extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_id", nullable = false)
    private Result result;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checkpoint_id", nullable = false)
    private Checkpoint checkpoint;

    @Column(name = "gun_time_ms")
    private Duration gunTime;

    @Column(name = "chip_time_ms")
    private Duration chipTime;

    @Positive
    @Column(name = "overall_place")
    private Integer overallPlace;

    @Positive
    @Column(name = "gender_place")
    private Integer genderPlace;

    @Positive
    @Column(name = "category_place")
    private Integer categoryPlace;

    @Positive
    @Column(name = "net_overall_place")
    private Integer netOverallPlace;

    @Positive
    @Column(name = "net_gender_place")
    private Integer netGenderPlace;

    @Positive
    @Column(name = "net_category_place")
    private Integer netCategoryPlace;

    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    public Checkpoint getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Checkpoint checkpoint) {
        this.checkpoint = checkpoint;
    }

    public Duration getGunTime() {
        return gunTime;
    }

    public void setGunTime(Duration gunTime) {
        this.gunTime = gunTime;
    }

    public Duration getChipTime() {
        return chipTime;
    }

    public void setChipTime(Duration chipTime) {
        this.chipTime = chipTime;
    }

    public Integer getOverallPlace() {
        return overallPlace;
    }

    public void setOverallPlace(Integer overallPlace) {
        this.overallPlace = overallPlace;
    }

    public Integer getGenderPlace() {
        return genderPlace;
    }

    public void setGenderPlace(Integer genderPlace) {
        this.genderPlace = genderPlace;
    }

    public Integer getCategoryPlace() {
        return categoryPlace;
    }

    public void setCategoryPlace(Integer categoryPlace) {
        this.categoryPlace = categoryPlace;
    }

    public Integer getNetOverallPlace() {
        return netOverallPlace;
    }

    public void setNetOverallPlace(Integer netOverallPlace) {
        this.netOverallPlace = netOverallPlace;
    }

    public Integer getNetGenderPlace() {
        return netGenderPlace;
    }

    public void setNetGenderPlace(Integer netGenderPlace) {
        this.netGenderPlace = netGenderPlace;
    }

    public Integer getNetCategoryPlace() {
        return netCategoryPlace;
    }

    public void setNetCategoryPlace(Integer netCategoryPlace) {
        this.netCategoryPlace = netCategoryPlace;
    }
}
