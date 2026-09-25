package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Entity
@Table(name = "event_series")
public class EventSeries extends BaseEntity {

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160, unique = true)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "default_result_inquiry_enabled", nullable = false)
    private boolean defaultResultInquiryEnabled;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "default_result_inquiry_deadline_mode", nullable = false, length = 32)
    private ResultInquiryDeadlineMode defaultResultInquiryDeadlineMode = ResultInquiryDeadlineMode.AFTER_EVENT_DAYS;

    @Positive
    @Column(name = "default_result_inquiry_window_days")
    private Integer defaultResultInquiryWindowDays;

    @Column(name = "default_result_inquiry_fixed_date")
    private LocalDate defaultResultInquiryFixedDate;

    @Email
    @Size(max = 320)
    @Column(name = "default_result_inquiry_email", length = 320)
    private String defaultResultInquiryEmail;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isDefaultResultInquiryEnabled() { return defaultResultInquiryEnabled; }

    public void setDefaultResultInquiryEnabled(boolean defaultResultInquiryEnabled) {
        this.defaultResultInquiryEnabled = defaultResultInquiryEnabled;
    }

    public ResultInquiryDeadlineMode getDefaultResultInquiryDeadlineMode() {
        return defaultResultInquiryDeadlineMode;
    }

    public void setDefaultResultInquiryDeadlineMode(ResultInquiryDeadlineMode mode) {
        this.defaultResultInquiryDeadlineMode = mode;
    }

    public Integer getDefaultResultInquiryWindowDays() { return defaultResultInquiryWindowDays; }

    public void setDefaultResultInquiryWindowDays(Integer defaultResultInquiryWindowDays) {
        this.defaultResultInquiryWindowDays = defaultResultInquiryWindowDays;
    }

    public LocalDate getDefaultResultInquiryFixedDate() { return defaultResultInquiryFixedDate; }

    public void setDefaultResultInquiryFixedDate(LocalDate defaultResultInquiryFixedDate) {
        this.defaultResultInquiryFixedDate = defaultResultInquiryFixedDate;
    }

    public String getDefaultResultInquiryEmail() { return defaultResultInquiryEmail; }

    public void setDefaultResultInquiryEmail(String defaultResultInquiryEmail) {
        this.defaultResultInquiryEmail = defaultResultInquiryEmail;
    }
}
