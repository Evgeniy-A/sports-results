package ru.sportsresults.service;

import org.springframework.stereotype.Component;
import ru.sportsresults.api.dto.ImportErrorDto;
import ru.sportsresults.importing.TimingResultImportRow;
import ru.sportsresults.domain.ResultStatus;

import java.util.ArrayList;
import java.util.List;

@Component
public class ImportValidator {

    public List<ImportErrorDto> validate(List<TimingResultImportRow> rows) {
        List<ImportErrorDto> errors = new ArrayList<>();
        if (rows.isEmpty()) {
            errors.add(new ImportErrorDto(null, "file", "CSV contains no valid data rows"));
            return errors;
        }
        for (TimingResultImportRow row : rows) {
            checkLength(errors, row, "event", row.raceCode(), 255);
            checkLength(errors, row, "dorsal", row.bib(), 64);
            checkLength(errors, row, "name", row.firstName(), 160);
            checkLength(errors, row, "surname", row.lastName(), 160);
            checkLength(errors, row, "gender", row.gender(), 32);
            checkLength(errors, row, "category", row.category(), 255);
            checkLength(errors, row, "clusterCode", row.clusterCode(), 100);
            checkLength(errors, row, "clusterName", row.clusterName(), 255);
            checkLength(errors, row, "clusterSourceName", row.clusterSourceName(), 255);
            checkLength(errors, row, "status", row.status(), 64);
            if (row.status() == null || row.status().isBlank()) {
                errors.add(new ImportErrorDto(
                        row.sourceRowNumber(),
                        "status",
                        "Result status is required. Allowed values: " + ResultStatus.allowedCodes()
                ));
            } else if (ResultStatus.normalize(row.status()).isEmpty()) {
                errors.add(new ImportErrorDto(
                        row.sourceRowNumber(),
                        "status",
                        "Unknown result status. Allowed values: " + ResultStatus.allowedCodes()
                ));
            }
            String displayName = ImportDisplayName.from(row);
            if (displayName == null) {
                errors.add(new ImportErrorDto(
                        row.sourceRowNumber(),
                        "name",
                        "At least a name, surname, or bib is required"
                ));
            } else if (displayName.length() > 320) {
                errors.add(new ImportErrorDto(
                        row.sourceRowNumber(),
                        "name",
                        "Derived display name exceeds 320 characters"
                ));
            }
        }
        return errors;
    }

    private static void checkLength(
            List<ImportErrorDto> errors,
            TimingResultImportRow row,
            String field,
            String value,
            int maximum
    ) {
        if (value != null && value.length() > maximum) {
            errors.add(new ImportErrorDto(
                    row.sourceRowNumber(),
                    field,
                    "Value exceeds " + maximum + " characters"
            ));
        }
    }
}
