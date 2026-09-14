package ru.sportsresults.importing;

import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.RegistrationEntryKind;

import java.io.StringReader;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimingCsvParserTest {

    private final TimingCsvParser parser = new TimingCsvParser();

    @Test
    void parsesBothSyntheticFilesDespiteDifferentHeaderOrder() throws Exception {
        TimingCsvParseResult m52 = parser.parse(sample("results_m52_2025.csv"));
        TimingCsvParseResult gonka = parser.parse(sample("results_gonka2026.csv"));

        assertThat(m52.errors()).isEmpty();
        assertThat(m52.totalRows()).isEqualTo(31);
        assertThat(m52.rows()).hasSize(31);
        assertThat(m52.rows().getFirst().firstName()).isEqualTo("Синтетический");
        assertThat(m52.rows().getFirst().lastName()).isEqualTo("Участник01");
        assertThat(m52.rows().getFirst().raceCode()).isEqualTo("10 km");

        assertThat(gonka.errors()).isEmpty();
        assertThat(gonka.totalRows()).isEqualTo(16);
        assertThat(gonka.rows()).hasSize(16);
        assertThat(gonka.rows().getFirst().firstName()).isEqualTo("Синтетическая запись 01");
        assertThat(gonka.rows().getFirst().raceCode()).isEqualTo("mass");
        assertThat(gonka.rows().getFirst().lastName()).isNull();
    }

    @Test
    void preservesNullableFieldsAndAllObservedStatuses() throws Exception {
        List<TimingResultImportRow> m52 = parser.parse(sample("results_m52_2025.csv")).rows();
        List<TimingResultImportRow> gonka = parser.parse(sample("results_gonka2026.csv")).rows();

        assertThat(gonka).allMatch(row -> row.category() == null);
        assertThat(gonka).anyMatch(row -> row.gender() == null);
        assertThat(gonka).anyMatch(row -> row.birthDate() == null);

        assertThat(m52).extracting(TimingResultImportRow::status)
                .contains("finished", "notstarted", "quarantine", "running");
        assertThat(gonka).extracting(TimingResultImportRow::status)
                .contains("finished", "notstarted", "disqualified");

        assertThat(m52).filteredOn(row -> row.status().equals("notstarted"))
                .allMatch(row -> row.gunTime() == null && row.chipTime() == null);
        assertThat(m52).filteredOn(row -> row.status().equals("running"))
                .allMatch(row -> row.gunTime() == null && row.chipTime() == null);
        assertThat(m52).filteredOn(row -> row.status().equals("quarantine"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.gunTime()).isNotNull();
                    assertThat(row.overallPlace()).isNull();
                });
        assertThat(gonka).filteredOn(row -> row.status().equals("disqualified"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.gunTime()).isNotNull();
                    assertThat(row.overallPlace()).isNull();
                });
    }

    @Test
    void parsesIntegralDecimalMillisecondsWithoutUsingFloatingPoint() throws Exception {
        TimingResultImportRow row = parser.parse(sample("results_gonka2026.csv")).rows().stream()
                .filter(candidate -> "SYN-MS".equals(candidate.bib()))
                .findFirst()
                .orElseThrow();

        assertThat(row.gunTime()).isEqualTo(Duration.ofMillis(13_862_490));
        assertThat(row.chipTime()).isEqualTo(Duration.ofMillis(12_467_310));
        assertThat(row.overallPlace()).isEqualTo(5);
        assertThat(row.entryKind()).isEqualTo(RegistrationEntryKind.UNKNOWN);
    }

    @Test
    void keepsNonNumericBibAsTextAndDoesNotInferEntryKindFromRaceCode() throws Exception {
        String csv = """
                event,dorsal,status,name,surname,gender,birthdate,category,times.real_:::finish:::,times.official_:::finish:::,rankings_:::full-1:::,rankings.gen_:::full-1:::,rankings.cat_:::full-1:::,netrankings_:::full-1:::,netrankings.gen_:::full-1:::,netrankings.cat_:::full-1:::
                teams,A-001,finished,Команда Альфа,,,,,1234.0,1500.0,1.0,,,1.0,,
                """;

        TimingCsvParseResult result = parser.parse(new StringReader(csv));

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.bib()).isEqualTo("A-001");
            assertThat(row.raceCode()).isEqualTo("teams");
            assertThat(row.entryKind()).isEqualTo(RegistrationEntryKind.UNKNOWN);
            assertThat(row.lastName()).isNull();
            assertThat(row.gender()).isNull();
            assertThat(row.birthDate()).isNull();
        });
    }

    @Test
    void preservesDuplicateBibRowsWithinOneRace() throws Exception {
        List<TimingResultImportRow> duplicates = parser.parse(sample("results_gonka2026.csv")).rows().stream()
                .filter(row -> row.raceCode().equals("mass") && row.bib().equals("SYN-DUP"))
                .toList();

        assertThat(duplicates).hasSize(2);
        assertThat(duplicates).extracting(TimingResultImportRow::firstName)
                .containsExactlyInAnyOrder("Дубль-А", "Дубль-Б");
    }

    @Test
    void rejectsFractionalMillisecondsAsARowError() throws Exception {
        String csv = """
                name,surname,gender,birthdate,event,dorsal,category,status,times.official_:::finish:::,times.real_:::finish:::,rankings_:::full-1:::,rankings.gen_:::full-1:::,rankings.cat_:::full-1:::,netrankings_:::full-1:::,netrankings.gen_:::full-1:::,netrankings.cat_:::full-1:::
                Иван,Иванов,male,1990-01-01,10 km,B-7,,finished,1000.5,900.0,1.0,1.0,,1.0,1.0,
                """;

        TimingCsvParseResult result = parser.parse(new StringReader(csv));

        assertThat(result.totalRows()).isOne();
        assertThat(result.rows()).singleElement().satisfies(row -> {
            assertThat(row.gunTimeSource().state()).isEqualTo(SourceFieldState.EMPTY);
            assertThat(row.chipTimeSource().state()).isEqualTo(SourceFieldState.VALUE);
        });
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.sourceRowNumber()).isEqualTo(2);
            assertThat(error.column()).isEqualTo(TimingCsvParser.GUN_TIME);
        });
    }

    @Test
    void preservesUnknownFutureStatus() throws Exception {
        String csv = """
                event,dorsal,status,name,surname,gender,birthdate,category,times.real_:::finish:::,times.official_:::finish:::,rankings_:::full-1:::,rankings.gen_:::full-1:::,rankings.cat_:::full-1:::,netrankings_:::full-1:::,netrankings.gen_:::full-1:::,netrankings.cat_:::full-1:::
                future-race,X-9,pending-review,Иван,Иванов,,,,,,,,,,,
                """;

        TimingCsvParseResult result = parser.parse(new StringReader(csv));

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).singleElement()
                .extracting(TimingResultImportRow::status)
                .isEqualTo("pending-review");
    }

    @Test
    void rejectsFutureBirthDateWithoutFallingBackToSourceCategory() throws Exception {
        String futureBirthDate = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        String csv = """
                name,surname,gender,birthdate,event,dorsal,category,status,times.official_:::finish:::,times.real_:::finish:::,rankings_:::full-1:::,rankings.gen_:::full-1:::,rankings.cat_:::full-1:::,netrankings_:::full-1:::,netrankings.gen_:::full-1:::,netrankings.cat_:::full-1:::
                Иван,Иванов,male,%s,10 km,B-8,30-39 Male,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """.formatted(futureBirthDate);

        TimingCsvParseResult result = parser.parse(new StringReader(csv));

        assertThat(result.rows()).singleElement()
                .satisfies(row -> assertThat(row.birthDateSource().state()).isEqualTo(SourceFieldState.EMPTY));
        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.column()).isEqualTo(TimingCsvParser.BIRTHDATE);
            assertThat(error.message()).contains("future");
        });
    }

    @Test
    void distinguishesAbsentEmptyAndValueSourceFieldsIncludingClusters() throws Exception {
        String withoutClusterColumns = """
                event,dorsal,status,name,surname,gender,birthdate,category,times.real_:::finish:::,times.official_:::finish:::,rankings_:::full-1:::,rankings.gen_:::full-1:::,rankings.cat_:::full-1:::,netrankings_:::full-1:::,netrankings.gen_:::full-1:::,netrankings.cat_:::full-1:::
                race-a,0010,finished,Иван,Иванов,,1990-01-01,,900.0,1000.0,1.0,1.0,,1.0,1.0,
                """;
        TimingResultImportRow absent = parser.parse(new StringReader(withoutClusterColumns)).rows().getFirst();

        assertThat(absent.clusterCodeSource().state()).isEqualTo(SourceFieldState.ABSENT);
        assertThat(absent.genderSource().state()).isEqualTo(SourceFieldState.EMPTY);
        assertThat(absent.birthDateSource()).isEqualTo(SourceField.value(LocalDate.of(1990, 1, 1)));
        assertThat(absent.gunTimeSource()).isEqualTo(SourceField.value(Duration.ofMillis(1_000)));

        String withClusterColumns = """
                event,dorsal,status,name,surname,gender,birthdate,category,times.real_:::finish:::,times.official_:::finish:::,rankings_:::full-1:::,rankings.gen_:::full-1:::,rankings.cat_:::full-1:::,netrankings_:::full-1:::,netrankings.gen_:::full-1:::,netrankings.cat_:::full-1:::,clusterCode,clusterName,clusterSourceName
                race-a,0010,finished,Иван,Иванов,,1990-01-01,,900.0,1000.0,1.0,1.0,,1.0,1.0,,A,,Source A
                """;
        TimingResultImportRow cluster = parser.parse(new StringReader(withClusterColumns)).rows().getFirst();

        assertThat(cluster.clusterCodeSource()).isEqualTo(SourceField.value("A"));
        assertThat(cluster.clusterNameSource().state()).isEqualTo(SourceFieldState.EMPTY);
        assertThat(cluster.clusterSourceNameSource()).isEqualTo(SourceField.value("Source A"));
    }

    @Test
    void treatsOmittedOptionalParticipantAndResultColumnsAsAbsent() throws Exception {
        TimingResultImportRow row = parser.parse(new StringReader("""
                event,dorsal,status,name
                race-a,0010,finished,Иван
                """)).rows().getFirst();

        assertThat(row.birthDateSource().state()).isEqualTo(SourceFieldState.ABSENT);
        assertThat(row.genderSource().state()).isEqualTo(SourceFieldState.ABSENT);
        assertThat(row.categorySource().state()).isEqualTo(SourceFieldState.ABSENT);
        assertThat(row.gunTimeSource().state()).isEqualTo(SourceFieldState.ABSENT);
        assertThat(row.chipTimeSource().state()).isEqualTo(SourceFieldState.ABSENT);
        assertThat(row.overallPlaceSource().state()).isEqualTo(SourceFieldState.ABSENT);
    }

    @Test
    void sourceFieldEnforcesItsStateInvariant() {
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> new SourceField<>(SourceFieldState.VALUE, null)))
                .isInstanceOf(NullPointerException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> new SourceField<>(SourceFieldState.ABSENT, "unexpected")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Path sample(String filename) {
        return Path.of("..", "samples", filename).toAbsolutePath().normalize();
    }
}
