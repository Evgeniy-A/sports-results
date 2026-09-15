package ru.sportsresults.importing;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import ru.sportsresults.domain.RegistrationEntryKind;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class TimingCsvParser {

    public static final String NAME = "name";
    public static final String SURNAME = "surname";
    public static final String GENDER = "gender";
    public static final String BIRTHDATE = "birthdate";
    public static final String EVENT = "event";
    public static final String DORSAL = "dorsal";
    public static final String CATEGORY = "category";
    public static final String STATUS = "status";
    public static final String GUN_TIME = "times.official_:::finish:::";
    public static final String CHIP_TIME = "times.real_:::finish:::";
    public static final String OVERALL_PLACE = "rankings_:::full-1:::";
    public static final String GENDER_PLACE = "rankings.gen_:::full-1:::";
    public static final String CATEGORY_PLACE = "rankings.cat_:::full-1:::";
    public static final String NET_OVERALL_PLACE = "netrankings_:::full-1:::";
    public static final String NET_GENDER_PLACE = "netrankings.gen_:::full-1:::";
    public static final String NET_CATEGORY_PLACE = "netrankings.cat_:::full-1:::";
    public static final String CLUSTER_CODE = "clusterCode";
    public static final String CLUSTER_NAME = "clusterName";
    public static final String CLUSTER_SOURCE_NAME = "clusterSourceName";

    private static final List<String> REQUIRED_HEADERS = List.of(
            EVENT, DORSAL, STATUS
    );

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .get();

    public TimingCsvParseResult parse(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    public TimingCsvParseResult parse(Reader reader) throws IOException {
        try (CSVParser csv = FORMAT.parse(reader)) {
            Map<String, Integer> headerMap = csv.getHeaderMap();
            validateHeaders(headerMap.keySet());

            List<TimingResultImportRow> rows = new ArrayList<>();
            List<TimingCsvRowError> errors = new ArrayList<>();
            int totalRows = 0;

            for (CSVRecord record : csv) {
                totalRows++;
                int sourceRowNumber = Math.toIntExact(record.getRecordNumber() + 1);
                SourceField<String> firstName = sourceText(record, headerMap, NAME);
                SourceField<String> lastName = sourceText(record, headerMap, SURNAME);
                SourceField<String> gender = sourceText(record, headerMap, GENDER);
                SourceField<LocalDate> birthDate = parseDate(record, headerMap, sourceRowNumber, errors);
                String raceCode = required(record, EVENT, sourceRowNumber, errors);
                String bib = nullable(record, DORSAL);
                SourceField<String> category = sourceText(record, headerMap, CATEGORY);
                SourceField<String> clusterCode = sourceText(record, headerMap, CLUSTER_CODE);
                SourceField<String> clusterName = sourceText(record, headerMap, CLUSTER_NAME);
                SourceField<String> clusterSourceName = sourceText(record, headerMap, CLUSTER_SOURCE_NAME);
                String status = required(record, STATUS, sourceRowNumber, errors);
                SourceField<Duration> gunTime = parseDuration(record, headerMap, GUN_TIME, sourceRowNumber, errors);
                SourceField<Duration> chipTime = parseDuration(record, headerMap, CHIP_TIME, sourceRowNumber, errors);
                SourceField<Integer> overallPlace = parsePlace(record, headerMap, OVERALL_PLACE, sourceRowNumber, errors);
                SourceField<Integer> genderPlace = parsePlace(record, headerMap, GENDER_PLACE, sourceRowNumber, errors);
                SourceField<Integer> categoryPlace = parsePlace(record, headerMap, CATEGORY_PLACE, sourceRowNumber, errors);
                SourceField<Integer> netOverallPlace = parsePlace(record, headerMap, NET_OVERALL_PLACE, sourceRowNumber, errors);
                SourceField<Integer> netGenderPlace = parsePlace(record, headerMap, NET_GENDER_PLACE, sourceRowNumber, errors);
                SourceField<Integer> netCategoryPlace = parsePlace(record, headerMap, NET_CATEGORY_PLACE, sourceRowNumber, errors);

                rows.add(new TimingResultImportRow(
                        sourceRowNumber,
                        hashRow(record, headerMap),
                        firstName,
                        lastName,
                        gender,
                        birthDate,
                        raceCode,
                        bib,
                        category,
                        clusterCode,
                        clusterName,
                        clusterSourceName,
                        status,
                        RegistrationEntryKind.UNKNOWN,
                        gunTime,
                        chipTime,
                        overallPlace,
                        genderPlace,
                        categoryPlace,
                        netOverallPlace,
                        netGenderPlace,
                        netCategoryPlace
                ));
            }

            return new TimingCsvParseResult(totalRows, rows, errors);
        }
    }

    private static void validateHeaders(Set<String> actualHeaders) {
        List<String> missing = REQUIRED_HEADERS.stream()
                .filter(header -> !actualHeaders.contains(header))
                .toList();
        if (!missing.isEmpty()) {
            throw new TimingCsvFormatException("Missing required CSV headers: " + String.join(", ", missing));
        }
    }

    private static String required(
            CSVRecord record,
            String column,
            int rowNumber,
            List<TimingCsvRowError> errors
    ) {
        String value = nullable(record, column);
        if (value == null) {
            errors.add(new TimingCsvRowError(rowNumber, column, "Required value is blank"));
        }
        return value;
    }

    private static String nullable(CSVRecord record, String column) {
        String value = record.get(column).strip();
        return value.isEmpty() ? null : value;
    }

    private static SourceField<String> sourceText(
            CSVRecord record,
            Map<String, Integer> headerMap,
            String column
    ) {
        if (!headerMap.containsKey(column)) {
            return SourceField.absent();
        }
        String value = record.get(column).strip();
        return value.isEmpty() ? SourceField.empty() : SourceField.value(value);
    }

    private static SourceField<LocalDate> parseDate(
            CSVRecord record,
            Map<String, Integer> headerMap,
            int rowNumber,
            List<TimingCsvRowError> errors
    ) {
        SourceField<String> source = sourceText(record, headerMap, BIRTHDATE);
        if (!source.hasValue()) {
            return source.isAbsent() ? SourceField.absent() : SourceField.empty();
        }
        try {
            LocalDate parsed = LocalDate.parse(source.value());
            if (parsed.isAfter(LocalDate.now(ZoneOffset.UTC))) {
                errors.add(new TimingCsvRowError(rowNumber, BIRTHDATE, "Birth date cannot be in the future"));
                return SourceField.empty();
            }
            return SourceField.value(parsed);
        } catch (DateTimeException exception) {
            errors.add(new TimingCsvRowError(rowNumber, BIRTHDATE, "Expected an ISO date (yyyy-MM-dd)"));
            return SourceField.empty();
        }
    }

    private static SourceField<Duration> parseDuration(
            CSVRecord record,
            Map<String, Integer> headerMap,
            String column,
            int rowNumber,
            List<TimingCsvRowError> errors
    ) {
        SourceField<BigInteger> milliseconds = parseIntegralNumber(record, headerMap, column, rowNumber, errors);
        if (!milliseconds.hasValue()) {
            return milliseconds.isAbsent() ? SourceField.absent() : SourceField.empty();
        }
        if (milliseconds.value().signum() < 0) {
            errors.add(new TimingCsvRowError(rowNumber, column, "Duration cannot be negative"));
            return SourceField.empty();
        }
        try {
            return SourceField.value(Duration.ofMillis(milliseconds.value().longValueExact()));
        } catch (ArithmeticException exception) {
            errors.add(new TimingCsvRowError(rowNumber, column, "Duration does not fit in BIGINT milliseconds"));
            return SourceField.empty();
        }
    }

    private static SourceField<Integer> parsePlace(
            CSVRecord record,
            Map<String, Integer> headerMap,
            String column,
            int rowNumber,
            List<TimingCsvRowError> errors
    ) {
        SourceField<BigInteger> place = parseIntegralNumber(record, headerMap, column, rowNumber, errors);
        if (!place.hasValue()) {
            return place.isAbsent() ? SourceField.absent() : SourceField.empty();
        }
        if (place.value().signum() <= 0) {
            errors.add(new TimingCsvRowError(rowNumber, column, "Place must be greater than zero"));
            return SourceField.empty();
        }
        try {
            return SourceField.value(place.value().intValueExact());
        } catch (ArithmeticException exception) {
            errors.add(new TimingCsvRowError(rowNumber, column, "Place does not fit in INTEGER"));
            return SourceField.empty();
        }
    }

    private static SourceField<BigInteger> parseIntegralNumber(
            CSVRecord record,
            Map<String, Integer> headerMap,
            String column,
            int rowNumber,
            List<TimingCsvRowError> errors
    ) {
        SourceField<String> source = sourceText(record, headerMap, column);
        if (!source.hasValue()) {
            return source.isAbsent() ? SourceField.absent() : SourceField.empty();
        }
        try {
            return SourceField.value(new BigDecimal(source.value()).toBigIntegerExact());
        } catch (NumberFormatException | ArithmeticException exception) {
            errors.add(new TimingCsvRowError(rowNumber, column, "Expected an integral decimal number"));
            return SourceField.empty();
        }
    }

    private static String hashRow(CSVRecord record, Map<String, Integer> headerMap) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<String> headers = headerMap.keySet().stream().sorted(Comparator.naturalOrder()).toList();
            for (String header : headers) {
                byte[] name = header.getBytes(StandardCharsets.UTF_8);
                byte[] value = record.get(header).getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(name.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(name);
                digest.update((byte) '=');
                digest.update(Integer.toString(value.length).getBytes(StandardCharsets.US_ASCII));
                digest.update((byte) ':');
                digest.update(value);
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
