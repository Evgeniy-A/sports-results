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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import ru.sportsresults.importing.ImportPreviewAction;
import ru.sportsresults.importing.ImportPreviewDecision;

@Entity
@Table(name = "import_operation_items", uniqueConstraints =
        @UniqueConstraint(name = "uk_import_operation_items_row", columnNames = {"operation_id", "source_row_number"}))
public class ImportOperationItem extends BaseEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "operation_id", nullable = false)
    private ImportOperation operation;

    @Positive
    @Column(name = "source_row_number")
    private Integer sourceRowNumber;

    @Size(max = 64)
    @Column(length = 64)
    private String bib;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImportPreviewDecision decision;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImportPreviewAction action;

    @Column(name = "registration_id")
    private Long registrationId;

    @Column(name = "result_id")
    private Long resultId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_race_id", nullable = false)
    private Race targetRace;

    public ImportOperation getOperation() { return operation; }
    public void setOperation(ImportOperation operation) { this.operation = operation; }
    public Integer getSourceRowNumber() { return sourceRowNumber; }
    public void setSourceRowNumber(Integer sourceRowNumber) { this.sourceRowNumber = sourceRowNumber; }
    public String getBib() { return bib; }
    public void setBib(String bib) { this.bib = bib; }
    public ImportPreviewDecision getDecision() { return decision; }
    public void setDecision(ImportPreviewDecision decision) { this.decision = decision; }
    public ImportPreviewAction getAction() { return action; }
    public void setAction(ImportPreviewAction action) { this.action = action; }
    public Long getRegistrationId() { return registrationId; }
    public void setRegistrationId(Long registrationId) { this.registrationId = registrationId; }
    public Long getResultId() { return resultId; }
    public void setResultId(Long resultId) { this.resultId = resultId; }
    public Race getTargetRace() { return targetRace; }
    public void setTargetRace(Race targetRace) { this.targetRace = targetRace; }
}
