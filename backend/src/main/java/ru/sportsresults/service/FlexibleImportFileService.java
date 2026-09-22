package ru.sportsresults.service;

import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.ImportFileAnalysisDto;
import ru.sportsresults.api.dto.ImportMappingProfileDto;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.RegistrationEntryKind;
import ru.sportsresults.importing.CanonicalImportField;
import ru.sportsresults.importing.ImportColumnAliases;
import ru.sportsresults.importing.ImportFileType;
import ru.sportsresults.importing.ImportHeaderNormalizer;
import ru.sportsresults.importing.ImportInputConfig;
import ru.sportsresults.importing.SourceField;
import ru.sportsresults.importing.TabularImportFile;
import ru.sportsresults.importing.TabularImportFileReader;
import ru.sportsresults.importing.TimingCsvFormatException;
import ru.sportsresults.importing.TimingCsvParseResult;
import ru.sportsresults.importing.TimingCsvParser;
import ru.sportsresults.importing.TimingCsvRowError;
import ru.sportsresults.importing.TimingResultImportRow;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.RaceRepository;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class FlexibleImportFileService {

    private static final int MAX_FILE_BYTES = 25 * 1024 * 1024;
    private static final Pattern DURATION = Pattern.compile("^(\\d+):([0-5]\\d):([0-5]\\d)(?:[.,](\\d{1,3}))?$");
    private static final Set<String> LEGACY_REQUIRED = Set.of(
            TimingCsvParser.EVENT, TimingCsvParser.DORSAL, TimingCsvParser.STATUS
    );

    private final TabularImportFileReader reader;
    private final TimingCsvParser legacyParser;
    private final EventRepository eventRepository;
    private final RaceRepository raceRepository;
    private final ImportMappingProfileService profileService;
    private final ObjectMapper objectMapper;

    public FlexibleImportFileService(
            TabularImportFileReader reader,
            TimingCsvParser legacyParser,
            EventRepository eventRepository,
            RaceRepository raceRepository,
            ImportMappingProfileService profileService,
            ObjectMapper objectMapper
    ) {
        this.reader = reader;
        this.legacyParser = legacyParser;
        this.eventRepository = eventRepository;
        this.raceRepository = raceRepository;
        this.profileService = profileService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ImportFileAnalysisDto analyze(Long eventId, String filename, byte[] contents, Long targetRaceId) {
        return analyze(eventId, filename, contents, targetRaceId, ImportInputConfig.legacy());
    }

    @Transactional(readOnly = true)
    public ImportFileAnalysisDto analyze(
            Long eventId,
            String filename,
            byte[] contents,
            Long targetRaceId,
            ImportInputConfig inputConfig
    ) {
        validateFile(contents);
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found");
        }
        List<Race> races = races(eventId);
        Race target = targetRaceId == null ? null : requireRace(eventId, targetRaceId, races);
        TabularImportFile file = read(filename, contents);
        validateTemplate(eventId, file, races);
        boolean generated = file.templateMetadata() != null;
        boolean legacy = isLegacyCsv(file);
        String signature = signature(file);
        ImportMappingProfileDto profile = generated || legacy ? null : profileService.exactMatch(file.fileType(), signature);

        LinkedHashSet<String> headers = file.sheets().stream()
                .flatMap(sheet -> sheet.headers().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, CanonicalImportField> configuredMappings = new LinkedHashMap<>();
        if (profile != null) configuredMappings.putAll(profile.mappings());
        if (inputConfig != null) configuredMappings.putAll(inputConfig.columnMappings());
        Map<String, CanonicalImportField> mappings = legacy
                ? Map.of()
                : automaticMappings(headers, configuredMappings);
        List<ImportFileAnalysisDto.Diagnostic> diagnostics = new ArrayList<>();
        duplicateCanonicalMappings(mappings).forEach(field -> diagnostics.add(new ImportFileAnalysisDto.Diagnostic(
                null, null, null, "DUPLICATE_CANONICAL_MAPPING",
                "Поле «" + field.displayName() + "» определено более чем одной колонкой"
        )));

        List<CanonicalImportField> missing = generated || legacy
                ? List.of()
                : java.util.Arrays.stream(CanonicalImportField.values())
                        .filter(CanonicalImportField::required)
                        .filter(field -> !mappings.containsValue(field))
                        .toList();
        for (CanonicalImportField field : missing) diagnostics.add(new ImportFileAnalysisDto.Diagnostic(
                null, null, null, "MISSING_REQUIRED_FIELD",
                "Не удалось определить обязательное поле: " + field.displayName()
        ));

        String discriminator = legacy ? TimingCsvParser.EVENT : headerFor(mappings, CanonicalImportField.RACE);
        if (!generated && !legacy && target == null && discriminator == null) {
            diagnostics.add(new ImportFileAnalysisDto.Diagnostic(
                    null, null, null, "RACE_MAPPING_REQUIRED",
                    "Выберите один Старт или сопоставьте колонку «Старт»"
            ));
        }
        List<ImportFileAnalysisDto.RaceValue> raceValues = generated
                ? List.of()
                : raceValues(file, discriminator, target, races);
        if (target == null) raceValues.stream().filter(value -> value.raceId() == null).forEach(value ->
                diagnostics.add(new ImportFileAnalysisDto.Diagnostic(
                        null, null, discriminator, "UNKNOWN_RACE_VALUE",
                        "Сопоставьте значение Старта: " + value.sourceValue()
                )));

        List<Long> resolvedRaceIds = generated
                ? file.templateMetadata().raceIds()
                : target != null
                        ? List.of(target.getId())
                        : raceValues.stream().map(ImportFileAnalysisDto.RaceValue::raceId)
                                .filter(Objects::nonNull).distinct().sorted().toList();

        List<ImportFileAnalysisDto.Column> columns = headers.stream().map(header -> {
            Set<CanonicalImportField> candidates = ImportColumnAliases.candidates(header);
            CanonicalImportField mapped = mappings.get(header);
            boolean profileMapped = profile != null && profile.mappings().containsKey(header);
            return new ImportFileAnalysisDto.Column(
                    header, ImportHeaderNormalizer.normalize(header), mapped,
                    mapped != null && !profileMapped, candidates.stream().toList()
            );
        }).toList();
        List<ImportFileAnalysisDto.Sheet> sheets = file.sheets().stream()
                .map(sheet -> new ImportFileAnalysisDto.Sheet(
                        sheet.name(), sheet.rows().size(), sheet.metadataRaceId()
                )).toList();
        List<ImportFileAnalysisDto.CanonicalField> canonical = java.util.Arrays.stream(CanonicalImportField.values())
                .map(field -> new ImportFileAnalysisDto.CanonicalField(field, field.displayName(), field.required()))
                .toList();

        return new ImportFileAnalysisDto(
                safeFilename(filename), file.fileType(), generated, legacy,
                generated ? file.templateMetadata().formatVersion() : null,
                generated ? file.templateMetadata().eventId() : null,
                signature, sheets, columns, mappings, canonical, missing, discriminator,
                raceValues, resolvedRaceIds, profile, diagnostics, diagnostics.isEmpty()
        );
    }

    @Transactional(readOnly = true)
    public TimingCsvParseResult parse(
            Long eventId,
            String filename,
            byte[] contents,
            ImportInputConfig config
    ) {
        validateFile(contents);
        ImportInputConfig effective = config == null ? ImportInputConfig.legacy() : config;
        TabularImportFile file = read(filename, contents);
        List<Race> races = races(eventId);
        validateTemplate(eventId, file, races);
        if (isLegacyCsv(file) && effective.columnMappings().isEmpty()
                && effective.targetRaceId() == null && effective.raceMappings().isEmpty()) {
            try (InputStreamReader stream = new InputStreamReader(
                    new ByteArrayInputStream(contents), StandardCharsets.UTF_8
            )) {
                return legacyParser.parse(stream);
            } catch (Exception exception) {
                if (exception instanceof TimingCsvFormatException format) throw format;
                throw new TimingCsvFormatException("CSV could not be read");
            }
        }

        Race target = effective.targetRaceId() == null
                ? null : requireRace(eventId, effective.targetRaceId(), races);
        validatePersistedRaceMappings(effective, races);
        LinkedHashSet<String> headers = file.sheets().stream()
                .flatMap(sheet -> sheet.headers().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, CanonicalImportField> mappings = automaticMappings(headers, effective.columnMappings());
        Set<CanonicalImportField> duplicates = duplicateCanonicalMappings(mappings);
        if (!duplicates.isEmpty()) {
            throw new TimingCsvFormatException("One Sports Results field is mapped from multiple columns: "
                    + duplicates.stream().map(CanonicalImportField::displayName).collect(Collectors.joining(", ")));
        }
        if (!file.sheets().isEmpty() && file.templateMetadata() == null) {
            for (CanonicalImportField field : CanonicalImportField.values()) {
                if (field.required() && !mappings.containsValue(field)) {
                    throw new TimingCsvFormatException("Missing required field: " + field.displayName());
                }
            }
        }

        Map<CanonicalImportField, String> headerByField = mappings.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getValue, Map.Entry::getKey, (left, right) -> left,
                        () -> new EnumMap<>(CanonicalImportField.class)
                ));
        Map<Long, Race> racesById = races.stream().collect(Collectors.toMap(Race::getId, race -> race));
        Map<String, Long> explicitRaceMappings = effective.raceMappings().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> normalizeValue(entry.getKey()), Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new
                ));
        List<TimingResultImportRow> rows = new ArrayList<>();
        List<TimingCsvRowError> errors = new ArrayList<>();
        int ordinal = 0;
        for (TabularImportFile.Sheet sheet : file.sheets()) {
            Long sheetRaceId = sheet.metadataRaceId();
            for (TabularImportFile.Row source : sheet.rows()) {
                ordinal++;
                Long raceId = resolveRaceId(
                        file, sheetRaceId, target, source, headerByField.get(CanonicalImportField.RACE),
                        explicitRaceMappings, races
                );
                Race race = raceId == null ? null : racesById.get(raceId);
                if (race == null) {
                    errors.add(error(ordinal, sheet, source, headerByField.get(CanonicalImportField.RACE),
                            "Старт не сопоставлен с мероприятием"));
                }
                SourceField<String> firstName = text(source, headerByField.get(CanonicalImportField.FIRST_NAME));
                SourceField<String> lastName = text(source, headerByField.get(CanonicalImportField.LAST_NAME));
                SourceField<String> fullName = text(source, headerByField.get(CanonicalImportField.FULL_NAME));
                if (firstName.isAbsent() && lastName.isAbsent() && fullName.hasValue()) firstName = fullName;
                SourceField<String> gender = mappedText(source, headerByField.get(CanonicalImportField.GENDER),
                        FlexibleImportFileService::normalizeGender);
                SourceField<LocalDate> birthDate = date(
                        ordinal, sheet, source, headerByField.get(CanonicalImportField.BIRTH_DATE), errors
                );
                String bib = text(source, headerByField.get(CanonicalImportField.BIB)).valueOrNull();
                SourceField<String> category = text(source, headerByField.get(CanonicalImportField.CATEGORY));
                SourceField<String> cluster = text(source, headerByField.get(CanonicalImportField.CLUSTER));
                SourceField<String> statusSource = mappedText(
                        source, headerByField.get(CanonicalImportField.STATUS), FlexibleImportFileService::normalizeStatus
                );
                String status = statusSource.valueOrNull();
                if (status == null) errors.add(error(
                        ordinal, sheet, source, headerByField.get(CanonicalImportField.STATUS), "Обязательное значение не заполнено"
                ));
                SourceField<Duration> gunTime = duration(
                        ordinal, file.fileType(), sheet, source, headerByField.get(CanonicalImportField.GUN_TIME), errors
                );
                SourceField<Duration> chipTime = duration(
                        ordinal, file.fileType(), sheet, source, headerByField.get(CanonicalImportField.CHIP_TIME), errors
                );
                SourceField<Integer> overall = integer(
                        ordinal, sheet, source, headerByField.get(CanonicalImportField.OVERALL_PLACE), errors
                );
                SourceField<Integer> genderPlace = integer(
                        ordinal, sheet, source, headerByField.get(CanonicalImportField.GENDER_PLACE), errors
                );
                SourceField<Integer> categoryPlace = integer(
                        ordinal, sheet, source, headerByField.get(CanonicalImportField.CATEGORY_PLACE), errors
                );
                SourceField<Integer> netOverall = integer(
                        ordinal, sheet, source, headerByField.get(CanonicalImportField.NET_OVERALL_PLACE), errors
                );
                SourceField<Integer> netGender = integer(
                        ordinal, sheet, source, headerByField.get(CanonicalImportField.NET_GENDER_PLACE), errors
                );
                SourceField<Integer> netCategory = integer(
                        ordinal, sheet, source, headerByField.get(CanonicalImportField.NET_CATEGORY_PLACE), errors
                );
                rows.add(new TimingResultImportRow(
                        ordinal, hashRow(race == null ? null : race.getSourceCode(), source, mappings),
                        firstName, lastName, gender, birthDate,
                        race == null ? null : race.getSourceCode(), bib, category,
                        SourceField.absent(), SourceField.absent(), cluster,
                        status, RegistrationEntryKind.UNKNOWN, gunTime, chipTime,
                        overall, genderPlace, categoryPlace, netOverall, netGender, netCategory
                ));
            }
        }
        return new TimingCsvParseResult(ordinal, rows, errors);
    }

    public String serializeConfig(ImportInputConfig config) {
        try {
            return objectMapper.writeValueAsString(config == null ? ImportInputConfig.legacy() : config);
        } catch (Exception exception) {
            throw new InvalidRequestException("INVALID_IMPORT_OPTIONS", "Import options could not be serialized");
        }
    }

    public ImportInputConfig deserializeConfig(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) return ImportInputConfig.legacy();
        try {
            return objectMapper.readValue(json, ImportInputConfig.class);
        } catch (Exception exception) {
            throw new InvalidRequestException("INVALID_IMPORT_OPTIONS", "Import options could not be parsed");
        }
    }

    private TabularImportFile read(String filename, byte[] contents) {
        try {
            return reader.read(filename, contents);
        } catch (TimingCsvFormatException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TimingCsvFormatException("Import file could not be read");
        }
    }

    private static Map<String, CanonicalImportField> automaticMappings(
            Set<String> headers,
            Map<String, CanonicalImportField> explicit
    ) {
        Map<String, CanonicalImportField> result = new LinkedHashMap<>();
        for (String header : headers) {
            Set<CanonicalImportField> candidates = ImportColumnAliases.candidates(header);
            if (candidates.size() == 1) result.put(header, candidates.iterator().next());
        }
        if (explicit != null) {
            explicit.forEach((header, field) -> {
                if (headers.contains(header) && field != null) result.put(header, field);
            });
        }
        return result;
    }

    private static Set<CanonicalImportField> duplicateCanonicalMappings(Map<String, CanonicalImportField> mappings) {
        Map<CanonicalImportField, Long> counts = mappings.values().stream()
                .collect(Collectors.groupingBy(field -> field, () -> new EnumMap<>(CanonicalImportField.class), Collectors.counting()));
        return counts.entrySet().stream().filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<ImportFileAnalysisDto.RaceValue> raceValues(
            TabularImportFile file,
            String discriminator,
            Race target,
            List<Race> races
    ) {
        if (target != null) return List.of(new ImportFileAnalysisDto.RaceValue(
                target.getName(), target.getId(), target.getName(), true
        ));
        if (discriminator == null) return List.of();
        LinkedHashSet<String> values = file.sheets().stream().flatMap(sheet -> sheet.rows().stream())
                .map(row -> text(row, discriminator).valueOrNull())
                .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        return values.stream().map(value -> {
            Race race = exactRace(value, races);
            return new ImportFileAnalysisDto.RaceValue(
                    value, race == null ? null : race.getId(), race == null ? null : race.getName(), race != null
            );
        }).toList();
    }

    private static Long resolveRaceId(
            TabularImportFile file,
            Long sheetRaceId,
            Race target,
            TabularImportFile.Row row,
            String discriminator,
            Map<String, Long> explicit,
            List<Race> races
    ) {
        if (file.templateMetadata() != null) return sheetRaceId;
        if (target != null) return target.getId();
        String source = text(row, discriminator).valueOrNull();
        if (source == null) return null;
        Long mapped = explicit.get(normalizeValue(source));
        if (mapped != null) return mapped;
        Race exact = exactRace(source, races);
        return exact == null ? null : exact.getId();
    }

    private static Race exactRace(String source, List<Race> races) {
        String normalized = normalizeValue(source);
        List<Race> matches = races.stream().filter(race ->
                normalizeValue(race.getSourceCode()).equals(normalized)
                        || normalizeValue(race.getName()).equals(normalized)
        ).distinct().toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static String headerFor(Map<String, CanonicalImportField> mappings, CanonicalImportField field) {
        return mappings.entrySet().stream().filter(entry -> entry.getValue() == field)
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private void validateTemplate(Long eventId, TabularImportFile file, List<Race> races) {
        TabularImportFile.TemplateMetadata metadata = file.templateMetadata();
        if (metadata == null) return;
        if (metadata.formatVersion() != TimingXlsxTemplateService.TEMPLATE_FORMAT_VERSION) {
            throw new InvalidRequestException(
                    "UNSUPPORTED_TEMPLATE_VERSION",
                    "Версия шаблона не поддерживается. Скачайте актуальный шаблон результатов."
            );
        }
        if (!eventId.equals(metadata.eventId())) {
            throw new InvalidRequestException(
                    "TEMPLATE_EVENT_MISMATCH", "Этот шаблон создан для другого мероприятия"
            );
        }
        List<Long> current = races.stream().map(Race::getId).sorted().toList();
        if (!current.equals(metadata.raceIds())) {
            throw new RequestConflictException(
                    "STALE_EVENT_TEMPLATE",
                    "Структура мероприятия изменилась после создания этого файла. Скачайте актуальный шаблон результатов."
            );
        }
        List<Long> sheetRaceIds = file.sheets().stream().map(TabularImportFile.Sheet::metadataRaceId)
                .filter(Objects::nonNull).sorted().toList();
        if (!sheetRaceIds.equals(metadata.raceIds())) {
            throw new InvalidRequestException("INVALID_TEMPLATE_METADATA", "Служебные данные листов шаблона изменены");
        }
    }

    private static SourceField<String> text(TabularImportFile.Row row, String header) {
        if (header == null || !row.cells().containsKey(header)) return SourceField.absent();
        TabularImportFile.Cell cell = row.cells().get(header);
        String value = cell.text();
        if ((value == null || value.isBlank()) && cell.numericValue() != null) {
            value = cell.numericValue().stripTrailingZeros().toPlainString();
        }
        return value == null || value.isBlank() ? SourceField.empty() : SourceField.value(value.strip());
    }

    private static SourceField<String> mappedText(
            TabularImportFile.Row row,
            String header,
            java.util.function.Function<String, String> mapper
    ) {
        SourceField<String> source = text(row, header);
        if (!source.hasValue()) return source;
        return SourceField.value(mapper.apply(source.value()));
    }

    private static SourceField<LocalDate> date(
            int ordinal,
            TabularImportFile.Sheet sheet,
            TabularImportFile.Row row,
            String header,
            List<TimingCsvRowError> errors
    ) {
        if (header == null || !row.cells().containsKey(header)) return SourceField.absent();
        TabularImportFile.Cell cell = row.cells().get(header);
        if (cell.blank()) return SourceField.empty();
        try {
            LocalDate parsed;
            if (cell.numericValue() != null) {
                parsed = DateUtil.getLocalDateTime(cell.numericValue().doubleValue()).toLocalDate();
            } else {
                String value = cell.text().strip();
                parsed = value.matches("\\d{2}\\.\\d{2}\\.\\d{4}")
                        ? LocalDate.parse(value, java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu"))
                        : LocalDate.parse(value);
            }
            if (parsed.isAfter(LocalDate.now(ZoneOffset.UTC))) throw new DateTimeException("future");
            return SourceField.value(parsed);
        } catch (RuntimeException exception) {
            errors.add(error(ordinal, sheet, row, header, "Ожидается дата ДД.ММ.ГГГГ или значение даты Excel"));
            return SourceField.empty();
        }
    }

    private static SourceField<Duration> duration(
            int ordinal,
            ImportFileType fileType,
            TabularImportFile.Sheet sheet,
            TabularImportFile.Row row,
            String header,
            List<TimingCsvRowError> errors
    ) {
        if (header == null || !row.cells().containsKey(header)) return SourceField.absent();
        TabularImportFile.Cell cell = row.cells().get(header);
        if (cell.blank()) return SourceField.empty();
        try {
            BigInteger millis;
            if (cell.numericValue() != null && fileType == ImportFileType.XLSX) {
                millis = cell.numericValue().multiply(BigDecimal.valueOf(86_400_000L))
                        .setScale(0, java.math.RoundingMode.HALF_UP).toBigIntegerExact();
            } else {
                String value = cell.text() != null
                        ? cell.text().strip()
                        : cell.numericValue().stripTrailingZeros().toPlainString();
                Matcher matcher = DURATION.matcher(value);
                if (matcher.matches()) {
                    long hours = Long.parseLong(matcher.group(1));
                    long minutes = Long.parseLong(matcher.group(2));
                    long seconds = Long.parseLong(matcher.group(3));
                    String fraction = matcher.group(4);
                    long fractionMillis = fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
                    millis = BigInteger.valueOf(Math.addExact(
                            Math.addExact(Math.multiplyExact(hours, 3_600_000L), Math.multiplyExact(minutes, 60_000L)),
                            Math.addExact(Math.multiplyExact(seconds, 1_000L), fractionMillis)
                    ));
                } else {
                    millis = new BigDecimal(value).toBigIntegerExact();
                }
            }
            if (millis.signum() < 0) throw new ArithmeticException("negative");
            return SourceField.value(Duration.ofMillis(millis.longValueExact()));
        } catch (RuntimeException exception) {
            errors.add(error(ordinal, sheet, row, header, "Некорректный формат времени"));
            return SourceField.empty();
        }
    }

    private static SourceField<Integer> integer(
            int ordinal,
            TabularImportFile.Sheet sheet,
            TabularImportFile.Row row,
            String header,
            List<TimingCsvRowError> errors
    ) {
        if (header == null || !row.cells().containsKey(header)) return SourceField.absent();
        TabularImportFile.Cell cell = row.cells().get(header);
        if (cell.blank()) return SourceField.empty();
        try {
            BigDecimal decimal = cell.numericValue() != null ? cell.numericValue() : new BigDecimal(cell.text().strip());
            int value = decimal.toBigIntegerExact().intValueExact();
            if (value <= 0) throw new ArithmeticException("non-positive");
            return SourceField.value(value);
        } catch (RuntimeException exception) {
            errors.add(error(ordinal, sheet, row, header, "Ожидается целое положительное место"));
            return SourceField.empty();
        }
    }

    private static TimingCsvRowError error(
            int ordinal,
            TabularImportFile.Sheet sheet,
            TabularImportFile.Row row,
            String header,
            String message
    ) {
        String location = "Лист «" + sheet.name() + "», строка " + row.rowNumber()
                + (header == null ? "" : ", поле «" + header + "»");
        return new TimingCsvRowError(ordinal, location, message);
    }

    private static String normalizeGender(String value) {
        return switch (normalizeValue(value)) {
            case "м", "муж", "мужчина", "male", "m" -> "male";
            case "ж", "жен", "женщина", "female", "f" -> "female";
            default -> value.strip();
        };
    }

    private static String normalizeStatus(String value) {
        return switch (normalizeValue(value)) {
            case "финишировал", "финиш", "finished" -> "finished";
            case "не стартовал", "не старт", "dns", "notstarted", "not started" -> "notstarted";
            case "дисквалифицирован", "дисквалификация", "dq", "dsq", "disqualified" -> "disqualified";
            case "карантин", "quarantine" -> "quarantine";
            case "на дистанции", "бежит", "running" -> "running";
            default -> value.strip();
        };
    }

    private static String hashRow(
            String raceCode,
            TabularImportFile.Row row,
            Map<String, CanonicalImportField> mappings
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Objects.toString(raceCode, "").getBytes(StandardCharsets.UTF_8));
            mappings.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> {
                digest.update((byte) '\n');
                digest.update(entry.getValue().name().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '=');
                TabularImportFile.Cell cell = row.cells().get(entry.getKey());
                String value = cell == null ? "" : cell.text() != null
                        ? cell.text() : Objects.toString(cell.numericValue(), "");
                digest.update(value.getBytes(StandardCharsets.UTF_8));
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String signature(TabularImportFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(file.fileType().name().getBytes(StandardCharsets.UTF_8));
            file.sheets().stream().flatMap(sheet -> sheet.headers().stream())
                    .map(ImportHeaderNormalizer::normalize).distinct().sorted()
                    .forEach(header -> {
                        digest.update((byte) '\n');
                        digest.update(header.getBytes(StandardCharsets.UTF_8));
                    });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean isLegacyCsv(TabularImportFile file) {
        return file.fileType() == ImportFileType.CSV && file.sheets().size() == 1
                && file.sheets().getFirst().headers().containsAll(LEGACY_REQUIRED);
    }

    private List<Race> races(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found");
        }
        return raceRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId);
    }

    private static Race requireRace(Long eventId, Long raceId, List<Race> races) {
        return races.stream().filter(race -> race.getId().equals(raceId)).findFirst()
                .orElseThrow(() -> new InvalidRequestException(
                        "INVALID_TARGET_RACE", "Selected Race does not belong to this Event"
                ));
    }

    private static void validatePersistedRaceMappings(ImportInputConfig config, List<Race> races) {
        if (!config.saveRaceMappings()) return;
        Set<Long> eventRaceIds = races.stream().map(Race::getId).collect(Collectors.toSet());
        Map<Long, List<String>> valuesByRace = new LinkedHashMap<>();
        Map<String, Long> requestedOwners = new LinkedHashMap<>();
        config.raceMappings().forEach((source, raceId) -> {
            String value = source == null ? "" : source.strip();
            if (value.isEmpty() || value.length() > 255 || raceId == null || !eventRaceIds.contains(raceId)) {
                throw new InvalidRequestException(
                        "INVALID_RACE_MAPPING", "Every saved Race mapping must be non-empty and belong to the Event"
                );
            }
            valuesByRace.computeIfAbsent(raceId, ignored -> new ArrayList<>()).add(value);
            Long previous = requestedOwners.put(normalizeValue(value), raceId);
            if (previous != null && !previous.equals(raceId)) {
                throw new RequestConflictException(
                        "RACE_SOURCE_CODE_CONFLICT", "External Race code is mapped to more than one Race"
                );
            }
        });
        valuesByRace.forEach((raceId, values) -> {
            if (values.stream().map(String::strip).distinct().count() != 1) {
                throw new InvalidRequestException(
                        "RACE_SOURCE_CODE_NOT_REPRESENTABLE",
                        "One Race can persist exactly one non-empty external source code"
                );
            }
        });
        Map<String, Long> existingOwners = races.stream().collect(Collectors.toMap(
                race -> normalizeValue(race.getSourceCode()), Race::getId,
                (left, right) -> left, LinkedHashMap::new
        ));
        requestedOwners.forEach((source, raceId) -> {
            Long owner = existingOwners.get(source);
            if (owner != null && !owner.equals(raceId)) {
                throw new RequestConflictException(
                        "RACE_SOURCE_CODE_CONFLICT", "External Race code is already assigned to another Race"
                );
            }
        });
    }

    private static void validateFile(byte[] contents) {
        if (contents == null || contents.length == 0) {
            throw new InvalidRequestException("EMPTY_IMPORT_FILE", "Uploaded file is empty");
        }
        if (contents.length > MAX_FILE_BYTES) {
            throw new InvalidRequestException("IMPORT_FILE_TOO_LARGE", "Uploaded file exceeds the 25 MiB limit");
        }
    }

    private static String normalizeValue(String value) {
        return ImportHeaderNormalizer.normalize(value);
    }

    private static String safeFilename(String originalFilename) {
        String value = originalFilename == null || originalFilename.isBlank() ? "upload" : originalFilename;
        String normalized = value.replace('\\', '/').replace("\u0000", "");
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).strip();
        return filename.isEmpty() ? "upload" : filename.substring(0, Math.min(255, filename.length()));
    }
}
