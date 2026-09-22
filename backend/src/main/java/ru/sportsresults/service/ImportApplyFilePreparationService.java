package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.api.dto.ImportErrorDto;
import ru.sportsresults.importing.TimingCsvFormatException;
import ru.sportsresults.importing.TimingCsvParseResult;
import ru.sportsresults.importing.ImportInputConfig;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
class ImportApplyFilePreparationService {

    private static final int MAX_FILE_BYTES = 25 * 1024 * 1024;

    private final FlexibleImportFileService flexibleImportFileService;
    private final ImportValidator validator;

    ImportApplyFilePreparationService(FlexibleImportFileService flexibleImportFileService, ImportValidator validator) {
        this.flexibleImportFileService = flexibleImportFileService;
        this.validator = validator;
    }

    String fileSha256(byte[] contents) {
        if (contents == null || contents.length == 0) {
            throw new InvalidRequestException("EMPTY_IMPORT_FILE", "Uploaded CSV is empty");
        }
        if (contents.length > MAX_FILE_BYTES) {
            throw new InvalidRequestException("IMPORT_FILE_TOO_LARGE", "Uploaded CSV exceeds the 25 MiB limit");
        }
        return sha256(contents);
    }

    ImportInputConfig deserializeConfig(String json) {
        return flexibleImportFileService.deserializeConfig(json);
    }

    PreparedImportFile prepare(
            Long eventId,
            String sourceFilename,
            byte[] contents,
            String fileSha256,
            ImportInputConfig inputConfig
    ) {
        TimingCsvParseResult parsed;
        try {
            parsed = flexibleImportFileService.parse(eventId, sourceFilename, contents, inputConfig);
        } catch (TimingCsvFormatException exception) {
            throw new InvalidRequestException("INVALID_IMPORT_FILE", exception.getMessage());
        } catch (Exception exception) {
            if (exception instanceof InvalidRequestException invalid) throw invalid;
            if (exception instanceof RequestConflictException conflict) throw conflict;
            throw new InvalidRequestException("IMPORT_FILE_READ_FAILED", "Import file could not be read");
        }
        List<ImportErrorDto> errors = new ArrayList<>();
        parsed.errors().forEach(error -> errors.add(new ImportErrorDto(
                error.sourceRowNumber(), error.column(), error.message()
        )));
        errors.addAll(validator.validate(parsed.rows()));
        return new PreparedImportFile(fileSha256, parsed, errors, inputConfig);
    }

    private static String sha256(byte[] contents) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
