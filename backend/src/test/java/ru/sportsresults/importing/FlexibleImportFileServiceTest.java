package ru.sportsresults.importing;

import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.sportsresults.api.dto.ImportFileAnalysisDto;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.Race;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.service.FlexibleImportFileService;
import ru.sportsresults.service.GeneratedImportTemplate;
import ru.sportsresults.service.ImportMappingProfileService;
import ru.sportsresults.service.RequestConflictException;
import ru.sportsresults.service.TimingXlsxTemplateService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlexibleImportFileServiceTest {

    @Test
    void generatedWorkbookContainsUsableSheetsMetadataFormatsAndValidation() throws Exception {
        Fixture fixture = fixture();
        GeneratedImportTemplate template = fixture.templateService().generate(7L);

        assertThat(template.filename()).endsWith("-results-template.xlsx");
        assertThat(template.contents()).startsWith((byte) 'P', (byte) 'K');
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template.contents()))) {
            assertThat(workbook.getSheet("Инструкция")).isNotNull();
            assertThat(workbook.getSheet("Масс-старт 5 км")).isNotNull();
            assertThat(workbook.getSheet("Чемпионат")).isNotNull();
            int metadataIndex = workbook.getSheetIndex(TabularImportFileReader.META_SHEET);
            assertThat(workbook.getSheetVisibility(metadataIndex)).isEqualTo(SheetVisibility.VERY_HIDDEN);
            assertThat(workbook.getSheetAt(metadataIndex).getRow(0).getCell(1).getStringCellValue()).isEqualTo("1");
            assertThat(workbook.getSheetAt(metadataIndex).getRow(1).getCell(1).getStringCellValue()).isEqualTo("7");

            var resultSheet = workbook.getSheet("Масс-старт 5 км");
            assertThat(resultSheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Стартовый номер");
            assertThat(java.util.stream.IntStream.range(0, resultSheet.getRow(1).getLastCellNum())
                    .mapToObj(index -> resultSheet.getRow(1).getCell(index).getStringCellValue()).toList())
                    .doesNotContain("sourceCode", "raceId", "eventId");
            assertThat(resultSheet.getRow(2).getCell(4).getCellStyle().getDataFormatString()).isEqualTo("dd.mm.yyyy");
            assertThat(resultSheet.getRow(2).getCell(5).getCellStyle().getDataFormatString())
                    .isEqualTo("[h]:mm:ss.000");
            assertThat(resultSheet.getDataValidations()).hasSize(2);
            assertThat(resultSheet.getPaneInformation()).isNotNull();
            assertThat(resultSheet.getCTWorksheet().isSetAutoFilter()).isTrue();
        }
    }

    @Test
    void generatedWorkbookImportsWithoutMappingAfterSheetRenameAndReorder() throws Exception {
        Fixture fixture = fixture();
        byte[] populated = populatedTemplate(fixture.templateService().generate(7L).contents());

        ImportFileAnalysisDto analysis = fixture.flexibleService().analyze(
                7L, "renamed.xlsx", populated, null
        );
        assertThat(analysis.sportsResultsTemplate()).isTrue();
        assertThat(analysis.resolvedRaceIds()).containsExactly(11L, 12L);
        assertThat(analysis.readyForValidation()).isTrue();

        TimingCsvParseResult parsed = fixture.flexibleService().parse(
                7L, "renamed.xlsx", populated, ImportInputConfig.legacy()
        );
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).hasSize(1);
        TimingResultImportRow row = parsed.rows().getFirst();
        assertThat(row.raceCode()).isEqualTo("5KM");
        assertThat(row.bib()).isEqualTo("SYN-501");
        assertThat(row.lastName()).isEqualTo("Тестов");
        assertThat(row.firstName()).isEqualTo("Алексей");
        assertThat(row.gender()).isEqualTo("male");
        assertThat(row.birthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(row.gunTime()).isEqualTo(Duration.ofHours(27).plusMinutes(3).plusSeconds(4).plusMillis(125));
        assertThat(row.chipTime()).isEqualTo(Duration.ofMillis(13_862_490));
        assertThat(row.status()).isEqualTo("finished");
    }

    @Test
    void staleGeneratedWorkbookIsRejectedWhenRaceSetChanges() {
        Fixture original = fixture();
        byte[] template = original.templateService().generate(7L).contents();
        Race added = race(13L, "10KM", "10 км", 2);
        when(original.raceRepository().findAllByEventIdOrderByDisplayOrderAscIdAsc(7L))
                .thenReturn(List.of(original.races().get(0), original.races().get(1), added));

        assertThatThrownBy(() -> original.flexibleService().analyze(7L, "stale.xlsx", template, null))
                .isInstanceOf(RequestConflictException.class)
                .hasMessageContaining("Структура мероприятия изменилась");
    }

    @Test
    void externalCsvSupportsAliasesShuffledColumnsUnknownColumnsAndSingleRace() {
        Fixture fixture = fixture();
        byte[] csv = resource("external-ru-single.csv");

        ImportFileAnalysisDto analysis = fixture.flexibleService().analyze(7L, "supplier.csv", csv, 11L);
        assertThat(analysis.columnMappings()).containsEntry(" НОМЕР ", CanonicalImportField.BIB)
                .containsEntry("Net Time", CanonicalImportField.CHIP_TIME)
                .containsEntry("Дата рождения", CanonicalImportField.BIRTH_DATE);
        assertThat(analysis.columnMappings()).doesNotContainKey("Комментарий");
        assertThat(analysis.readyForValidation()).isTrue();

        TimingCsvParseResult parsed = fixture.flexibleService().parse(
                7L, "supplier.csv", csv,
                new ImportInputConfig(11L, analysis.columnMappings(), Map.of(), false)
        );
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows().getFirst().chipTime()).isEqualTo(Duration.ofMillis(13_862_490));
        assertThat(parsed.rows().getFirst().bib()).isEqualTo("SYN-RU-77");
        assertThat(parsed.rows().getFirst().raceCode()).isEqualTo("5KM");
        assertThat(parsed.rows().getFirst().birthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
    }

    @Test
    void ambiguousHeaderIsNotAutoMappedAndMultiRaceCodesResolveExactly() {
        Fixture fixture = fixture();
        byte[] ambiguous = resource("external-ambiguous.csv");
        ImportFileAnalysisDto ambiguousAnalysis = fixture.flexibleService().analyze(
                7L, "ambiguous.csv", ambiguous, 11L
        );
        assertThat(ambiguousAnalysis.columnMappings()).doesNotContainKey("Время");
        assertThat(ambiguousAnalysis.columns()).filteredOn(column -> column.header().equals("Время"))
                .singleElement().satisfies(column -> assertThat(column.candidates())
                        .containsExactlyInAnyOrder(CanonicalImportField.GUN_TIME, CanonicalImportField.CHIP_TIME));

        byte[] multi = resource("external-multi.csv");
        ImportFileAnalysisDto multiAnalysis = fixture.flexibleService().analyze(7L, "multi.csv", multi, null);
        assertThat(multiAnalysis.raceValues()).extracting(ImportFileAnalysisDto.RaceValue::raceId)
                .containsExactly(11L, 12L);
        assertThat(multiAnalysis.readyForValidation()).isTrue();
    }

    @Test
    void compatibleSportsResultsCsvExposesColumnsAndMatchesEquivalentDistanceLabels() throws Exception {
        Fixture fixture = fixture();
        fixture.races().get(0).setSourceCode("M52-10");
        fixture.races().get(0).setName("10 км");
        fixture.races().get(1).setSourceCode("M52-42");
        fixture.races().get(1).setName("42,2 км");
        byte[] csv = Files.readAllBytes(Path.of("..", "samples", "results_m52_2025.csv"));

        ImportFileAnalysisDto analysis = fixture.flexibleService().analyze(7L, "results_m52_2025.csv", csv, null);

        assertThat(analysis.legacyCsv()).isTrue();
        assertThat(analysis.columnMappings())
                .containsEntry("name", CanonicalImportField.FIRST_NAME)
                .containsEntry("surname", CanonicalImportField.LAST_NAME)
                .containsEntry("event", CanonicalImportField.RACE)
                .containsEntry("dorsal", CanonicalImportField.BIB)
                .containsEntry("status", CanonicalImportField.STATUS);
        assertThat(analysis.raceValues())
                .extracting(ImportFileAnalysisDto.RaceValue::sourceValue)
                .containsExactly("10 km", "42.2 km");
        assertThat(analysis.raceValues())
                .extracting(ImportFileAnalysisDto.RaceValue::raceId)
                .containsExactly(11L, 12L);
        assertThat(analysis.resolvedRaceIds()).containsExactly(11L, 12L);
        assertThat(analysis.readyForValidation()).isTrue();

        Map<String, Long> raceMappings = analysis.raceValues().stream().collect(java.util.stream.Collectors.toMap(
                ImportFileAnalysisDto.RaceValue::sourceValue,
                ImportFileAnalysisDto.RaceValue::raceId,
                (left, right) -> left,
                java.util.LinkedHashMap::new
        ));
        TimingCsvParseResult parsed = fixture.flexibleService().parse(
                7L,
                "results_m52_2025.csv",
                csv,
                new ImportInputConfig(null, analysis.columnMappings(), raceMappings, false)
        );
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).isNotEmpty();
        assertThat(parsed.rows()).extracting(TimingResultImportRow::raceCode)
                .containsOnly("M52-10", "M52-42");
    }

    @Test
    void ambiguousLegacyRaceRequiresAndAcceptsExplicitMapping() {
        Fixture fixture = fixture();
        fixture.races().get(0).setSourceCode("RACE-A");
        fixture.races().get(0).setName("10 км");
        fixture.races().get(1).setSourceCode("10 km");
        fixture.races().get(1).setName("Другой старт");
        byte[] csv = "event,dorsal,status,name\n10 km,SYN-1,finished,Участник\n"
                .getBytes(StandardCharsets.UTF_8);

        ImportFileAnalysisDto analysis = fixture.flexibleService().analyze(7L, "ambiguous.csv", csv, null);

        assertThat(analysis.raceValues()).singleElement().satisfies(value -> {
            assertThat(value.sourceValue()).isEqualTo("10 km");
            assertThat(value.raceId()).isNull();
            assertThat(value.automatic()).isFalse();
        });
        assertThat(analysis.readyForValidation()).isFalse();

        TimingCsvParseResult parsed = fixture.flexibleService().parse(
                7L,
                "ambiguous.csv",
                csv,
                new ImportInputConfig(null, analysis.columnMappings(), Map.of("10 km", 11L), false)
        );
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).singleElement()
                .extracting(TimingResultImportRow::raceCode)
                .isEqualTo("RACE-A");
    }

    @Test
    void headerNormalizationHandlesUnicodeSpacingSeparatorsAndYo() {
        assertThat(ImportHeaderNormalizer.normalize("  ДАТА__РОЖДЕНИЯ  ")).isEqualTo("дата рождения");
        assertThat(ImportHeaderNormalizer.normalize("Стартовый-номер")).isEqualTo("стартовый номер");
        assertThat(ImportHeaderNormalizer.normalize("Ёлочный   тест")).isEqualTo("елочный тест");
        assertThat(ImportColumnAliases.candidates("Bib_Number")).containsExactly(CanonicalImportField.BIB);
        assertThat(ImportColumnAliases.candidates("Start")).containsExactly(CanonicalImportField.RACE);
    }

    @Test
    void templateSecurityRejectsWrongEventUnsupportedVersionAndChangedSheetRaceId() throws Exception {
        Fixture fixture = fixture();
        byte[] template = fixture.templateService().generate(7L).contents();

        assertThatThrownBy(() -> fixture.flexibleService().analyze(
                7L, "wrong-event.xlsx", mutateMetadata(template, "eventId", "99"), null
        )).hasMessageContaining("другого мероприятия");
        assertThatThrownBy(() -> fixture.flexibleService().analyze(
                7L, "future.xlsx", mutateMetadata(template, "templateFormatVersion", "2"), null
        )).hasMessageContaining("Версия шаблона не поддерживается");
        assertThatThrownBy(() -> fixture.flexibleService().analyze(
                7L, "foreign-race.xlsx", mutateFirstSheetRaceId(template, 999L), null
        )).hasMessageContaining("Служебные данные листов шаблона изменены");
    }

    @Test
    void optionalTemplateFieldsMayStayBlankAndExternalXlsxUsesCanonicalLayer() throws Exception {
        Fixture fixture = fixture();
        byte[] minimal = minimalTemplate(fixture.templateService().generate(7L).contents());
        TimingCsvParseResult minimalParsed = fixture.flexibleService().parse(
                7L, "minimal.xlsx", minimal, ImportInputConfig.legacy()
        );
        assertThat(minimalParsed.errors()).isEmpty();
        assertThat(minimalParsed.rows()).singleElement().satisfies(row -> {
            assertThat(row.bib()).isEqualTo("SYN-MIN-1");
            assertThat(row.firstNameSource().isEmpty()).isTrue();
            assertThat(row.birthDateSource().isEmpty()).isTrue();
            assertThat(row.gunTimeSource().isEmpty()).isTrue();
        });

        byte[] external = externalXlsx();
        ImportFileAnalysisDto analysis = fixture.flexibleService().analyze(7L, "external.xlsx", external, 11L);
        assertThat(analysis.sportsResultsTemplate()).isFalse();
        assertThat(analysis.columnMappings()).containsEntry("Bib", CanonicalImportField.BIB)
                .containsEntry("Status", CanonicalImportField.STATUS)
                .containsEntry("Chip Time", CanonicalImportField.CHIP_TIME)
                .doesNotContainKey("Unused note");
        TimingCsvParseResult parsed = fixture.flexibleService().parse(
                7L, "external.xlsx", external,
                new ImportInputConfig(11L, analysis.columnMappings(), Map.of(), false)
        );
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows()).singleElement().satisfies(row -> {
            assertThat(row.bib()).isEqualTo("SYN-X-9");
            assertThat(row.chipTime()).isEqualTo(Duration.ofHours(25).plusMillis(321));
        });
    }

    @Test
    void shuffledEnglishCsvBomAndTabDelimiterRemainHeaderDriven() {
        Fixture fixture = fixture();
        byte[] shuffled = resource("external-en-shuffled.csv");
        ImportFileAnalysisDto analysis = fixture.flexibleService().analyze(7L, "english.csv", shuffled, 11L);
        TimingCsvParseResult parsed = fixture.flexibleService().parse(
                7L, "english.csv", shuffled,
                new ImportInputConfig(11L, analysis.columnMappings(), Map.of(), false)
        );
        assertThat(parsed.errors()).isEmpty();
        assertThat(parsed.rows().getFirst().chipTime()).isEqualTo(
                Duration.ofHours(1).plusMinutes(1).plusSeconds(2).plusMillis(3)
        );

        byte[] tab = ("\uFEFFBib\tStatus\tGun Time\nSYN-TAB-1\tfinished\t00:59:58.007\n")
                .getBytes(StandardCharsets.UTF_8);
        ImportFileAnalysisDto tabAnalysis = fixture.flexibleService().analyze(7L, "tab.csv", tab, 11L);
        assertThat(tabAnalysis.readyForValidation()).isTrue();
        assertThat(fixture.flexibleService().parse(
                7L, "tab.csv", tab,
                new ImportInputConfig(11L, tabAnalysis.columnMappings(), Map.of(), false)
        ).rows().getFirst().gunTime()).isEqualTo(Duration.ofMinutes(59).plusSeconds(58).plusMillis(7));
    }

    @Test
    void missingRequiredAndDuplicateCanonicalMappingsAreBlocked() {
        Fixture fixture = fixture();
        byte[] missing = "name,chip time\nSynthetic,01:00:00\n".getBytes(StandardCharsets.UTF_8);
        ImportFileAnalysisDto missingAnalysis = fixture.flexibleService().analyze(7L, "missing.csv", missing, 11L);
        assertThat(missingAnalysis.readyForValidation()).isFalse();
        assertThat(missingAnalysis.missingRequiredFields())
                .containsExactlyInAnyOrder(CanonicalImportField.BIB, CanonicalImportField.STATUS);

        byte[] duplicate = "Номер,Bib,Статус\nSYN-1,SYN-1,finished\n".getBytes(StandardCharsets.UTF_8);
        ImportInputConfig duplicateConfig = new ImportInputConfig(
                11L,
                Map.of("Номер", CanonicalImportField.BIB, "Bib", CanonicalImportField.BIB),
                Map.of(), false
        );
        ImportFileAnalysisDto duplicateAnalysis = fixture.flexibleService().analyze(
                7L, "duplicate.csv", duplicate, 11L, duplicateConfig
        );
        assertThat(duplicateAnalysis.readyForValidation()).isFalse();
        assertThat(duplicateAnalysis.diagnostics()).extracting(ImportFileAnalysisDto.Diagnostic::code)
                .contains("DUPLICATE_CANONICAL_MAPPING");
        assertThatThrownBy(() -> fixture.flexibleService().parse(
                7L, "duplicate.csv", duplicate, duplicateConfig
        )).hasMessageContaining("multiple columns");
    }

    private static byte[] populatedTemplate(byte[] source) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.getSheet("Масс-старт 5 км");
            var row = sheet.getRow(2);
            row.createCell(0).setCellValue("SYN-501");
            row.createCell(1).setCellValue("Тестов");
            row.createCell(2).setCellValue("Алексей");
            row.createCell(3).setCellValue("М");
            row.getCell(4).setCellValue(LocalDate.of(1990, 5, 17));
            row.getCell(5).setCellValue((27 * 3_600_000L + 3 * 60_000L + 4_125L) / 86_400_000d);
            row.getCell(6).setCellValue(13_862_490d / 86_400_000d);
            row.createCell(7).setCellValue("Финишировал");
            row.createCell(8).setCellValue("30–39");
            workbook.setSheetName(workbook.getSheetIndex(sheet), "Переименованный лист");
            workbook.setSheetOrder("Переименованный лист", workbook.getNumberOfSheets() - 2);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] minimalTemplate(byte[] source) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.getSheet("Масс-старт 5 км").getRow(2);
            row.createCell(0).setCellValue("SYN-MIN-1");
            row.createCell(7).setCellValue("Не стартовал");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] externalXlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Supplier export");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Unused note");
            header.createCell(1).setCellValue("Chip Time");
            header.createCell(2).setCellValue("Status");
            header.createCell(3).setCellValue("Bib");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("ignored");
            row.createCell(1).setCellValue(Duration.ofHours(25).plusMillis(321).toMillis() / 86_400_000d);
            row.createCell(2).setCellValue("finished");
            row.createCell(3).setCellValue("SYN-X-9");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] mutateMetadata(byte[] source, String key, String value) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var metadata = workbook.getSheet(TabularImportFileReader.META_SHEET);
            for (var row : metadata) {
                if (key.equals(row.getCell(0).getStringCellValue())) row.getCell(1).setCellValue(value);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] mutateFirstSheetRaceId(byte[] source, long raceId) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getSheet("Масс-старт 5 км").getRow(0).getCell(1).setCellValue(raceId);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] resource(String filename) {
        try (var stream = FlexibleImportFileServiceTest.class.getResourceAsStream("/import/" + filename)) {
            if (stream == null) throw new IllegalStateException("Missing synthetic fixture " + filename);
            return stream.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Fixture fixture() {
        EventRepository events = mock(EventRepository.class);
        RaceRepository races = mock(RaceRepository.class);
        ImportMappingProfileService profiles = mock(ImportMappingProfileService.class);
        Event event = new Event();
        ReflectionTestUtils.setField(event, "id", 7L);
        event.setName("Синтетическое мероприятие");
        event.setSlug("synthetic-event");
        Race first = race(11L, "5KM", "Масс-старт 5 км", 0);
        Race second = race(12L, "CHAMP", "Чемпионат", 1);
        when(events.existsById(7L)).thenReturn(true);
        when(events.findById(7L)).thenReturn(java.util.Optional.of(event));
        when(races.findAllByEventIdOrderByDisplayOrderAscIdAsc(7L)).thenReturn(List.of(first, second));
        when(profiles.exactMatch(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
        TimingXlsxTemplateService templateService = new TimingXlsxTemplateService(events, races);
        FlexibleImportFileService flexible = new FlexibleImportFileService(
                new TabularImportFileReader(), new TimingCsvParser(), events, races, profiles, null
        );
        return new Fixture(templateService, flexible, races, List.of(first, second));
    }

    private static Race race(Long id, String sourceCode, String name, int order) {
        Race race = new Race();
        ReflectionTestUtils.setField(race, "id", id);
        race.setSourceCode(sourceCode);
        race.setName(name);
        race.setDisplayOrder(order);
        return race;
    }

    private record Fixture(
            TimingXlsxTemplateService templateService,
            FlexibleImportFileService flexibleService,
            RaceRepository raceRepository,
            List<Race> races
    ) {}
}
