package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.api.dto.ImportErrorDto;
import ru.sportsresults.importing.TimingCsvFormatException;
import ru.sportsresults.importing.TimingCsvParseResult;
import ru.sportsresults.importing.TimingCsvParser;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
class ImportApplyFilePreparationService {

    private static final int MAX_FILE_BYTES = 25 * 1024 * 1024;

    private final TimingCsvParser parser;
    private final ImportValidator validator;

    ImportApplyFilePreparationService(TimingCsvParser parser, ImportValidator validator) {
        this.parser = parser;
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

    PreparedImportFile prepare(byte[] contents, String fileSha256) {
        TimingCsvParseResult parsed;
        try (InputStreamReader reader = new InputStreamReader(
                new ByteArrayInputStream(contents), StandardCharsets.UTF_8
        )) {
            parsed = parser.parse(reader);
        } catch (TimingCsvFormatException exception) {
            throw new InvalidRequestException("INVALID_CSV_HEADER", exception.getMessage());
        } catch (Exception exception) {
            throw new InvalidRequestException("IMPORT_FILE_READ_FAILED", "CSV could not be read");
        }
        List<ImportErrorDto> errors = new ArrayList<>();
        parsed.errors().forEach(error -> errors.add(new ImportErrorDto(
                error.sourceRowNumber(), error.column(), error.message()
        )));
        errors.addAll(validator.validate(parsed.rows()));
        return new PreparedImportFile(fileSha256, parsed, errors);
    }

    private static String sha256(byte[] contents) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
