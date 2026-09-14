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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registrations", uniqueConstraints =
        @UniqueConstraint(name = "uk_registrations_batch_row", columnNames = {"import_batch_id", "source_row_number"}))
public class Registration extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cluster_id")
    private StartCluster cluster;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_kind", nullable = false, length = 32)
    private RegistrationEntryKind entryKind = RegistrationEntryKind.UNKNOWN;

    @Size(max = 64)
    @Column(length = 64)
    private String bib;

    @NotBlank
    @Size(max = 320)
    @Column(name = "display_name", nullable = false, length = 320)
    private String displayName;

    @Size(max = 160)
    @Column(name = "first_name", length = 160)
    private String firstName;

    @Size(max = 160)
    @Column(name = "last_name", length = 160)
    private String lastName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Size(max = 255)
    @Column(name = "source_category", length = 255)
    private String sourceCategory;

    @Size(max = 32)
    @Column(length = 32)
    private String gender;

    @Column(name = "search_text", insertable = false, updatable = false)
    private String searchText;

    @Positive
    @Column(name = "source_row_number", nullable = false)
    private int sourceRowNumber;

    @NotBlank
    @Size(min = 64, max = 64)
    @Column(name = "source_row_hash", nullable = false, length = 64)
    private String sourceRowHash;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "retired_by_import_operation_id")
    private UUID retiredByImportOperationId;

    public Race getRace() {
        return race;
    }

    public void setRace(Race race) {
        this.race = race;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public StartCluster getCluster() { return cluster; }

    public void setCluster(StartCluster cluster) { this.cluster = cluster; }

    public ImportBatch getImportBatch() {
        return importBatch;
    }

    public void setImportBatch(ImportBatch importBatch) {
        this.importBatch = importBatch;
    }

    public RegistrationEntryKind getEntryKind() {
        return entryKind;
    }

    public void setEntryKind(RegistrationEntryKind entryKind) {
        this.entryKind = entryKind;
    }

    public String getBib() {
        return bib;
    }

    public void setBib(String bib) {
        this.bib = bib;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getSourceCategory() {
        return sourceCategory;
    }

    public void setSourceCategory(String sourceCategory) {
        this.sourceCategory = sourceCategory;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public int getSourceRowNumber() {
        return sourceRowNumber;
    }

    public void setSourceRowNumber(int sourceRowNumber) {
        this.sourceRowNumber = sourceRowNumber;
    }

    public String getSourceRowHash() {
        return sourceRowHash;
    }

    public void setSourceRowHash(String sourceRowHash) {
        this.sourceRowHash = sourceRowHash;
    }

    public Instant getRetiredAt() { return retiredAt; }
    public void setRetiredAt(Instant retiredAt) { this.retiredAt = retiredAt; }
    public UUID getRetiredByImportOperationId() { return retiredByImportOperationId; }
    public void setRetiredByImportOperationId(UUID value) { this.retiredByImportOperationId = value; }
    public boolean isCurrent() { return retiredAt == null; }
}
