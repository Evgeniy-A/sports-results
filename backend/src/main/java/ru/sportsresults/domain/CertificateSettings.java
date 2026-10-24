package ru.sportsresults.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "event_certificate_settings")
public class CertificateSettings extends BaseEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false, unique = true)
    private Event event;

    @Column(nullable = false)
    private boolean enabled;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "event_certificate_allowed_fields", joinColumns = @JoinColumn(name = "settings_id"))
    @Column(name = "field_name", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<CertificateField> allowedFields = new LinkedHashSet<>();

    public Event getEvent() { return event; }
    public void setEvent(Event event) { this.event = event; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Set<CertificateField> getAllowedFields() { return allowedFields; }
    public void setAllowedFields(Set<CertificateField> allowedFields) {
        this.allowedFields = new LinkedHashSet<>(allowedFields == null ? Set.of() : allowedFields);
    }
}
