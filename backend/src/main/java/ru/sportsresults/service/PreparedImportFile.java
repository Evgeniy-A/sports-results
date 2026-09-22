package ru.sportsresults.service;

import ru.sportsresults.api.dto.ImportErrorDto;
import ru.sportsresults.importing.TimingCsvParseResult;
import ru.sportsresults.importing.ImportInputConfig;

import java.util.List;

record PreparedImportFile(
        String fileSha256,
        TimingCsvParseResult parsed,
        List<ImportErrorDto> validationErrors,
        ImportInputConfig inputConfig
) {
    PreparedImportFile {
        validationErrors = List.copyOf(validationErrors);
    }
}
