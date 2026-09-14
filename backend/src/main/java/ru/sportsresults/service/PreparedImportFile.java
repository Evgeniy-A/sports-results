package ru.sportsresults.service;

import ru.sportsresults.api.dto.ImportErrorDto;
import ru.sportsresults.importing.TimingCsvParseResult;

import java.util.List;

record PreparedImportFile(
        String fileSha256,
        TimingCsvParseResult parsed,
        List<ImportErrorDto> validationErrors
) {
    PreparedImportFile {
        validationErrors = List.copyOf(validationErrors);
    }
}
