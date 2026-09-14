package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Duration;

@Entity
@Table(name = "results")
public class Result extends BaseEntity {

    @NotNull
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_id", nullable = false, unique = true)
    private Registration registration;

    @NotBlank
    @Size(max = 64)
    @Column(nullable = false, length = 64)
    private String status;

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

    public Registration getRegistration() {
        return registration;
    }

    public void setRegistration(Registration registration) {
        this.registration = registration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
