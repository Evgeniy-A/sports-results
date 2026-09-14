package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import ru.sportsresults.api.dto.ImportPreviewResponseDto;
import ru.sportsresults.domain.ImportOperationMode;
import ru.sportsresults.domain.RegistrationEntryKind;
import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.ResultsPublicationStatus;
import ru.sportsresults.importing.ImportPreviewAction;
import ru.sportsresults.importing.ImportPreviewDecision;
import ru.sportsresults.importing.SourceField;
import ru.sportsresults.importing.TimingCsvParseResult;
import ru.sportsresults.importing.TimingCsvParser;
import ru.sportsresults.importing.TimingResultImportRow;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportPreviewPlannerTest {

    private static final long EVENT_ID = 1L;
    private static final String FILE_SHA = "a".repeat(64);
    private static final LocalDate DOB = LocalDate.of(1990, 1, 1);
    private static final ImportPreviewDatabaseSnapshot.RaceSnapshot RACE_A =
            new ImportPreviewDatabaseSnapshot.RaceSnapshot(10L, "A", "Race A", 100L, "Individual");
    private static final ImportPreviewDatabaseSnapshot.RaceSnapshot RACE_B =
            new ImportPreviewDatabaseSnapshot.RaceSnapshot(20L, "B", "Race B", 200L, "Team");
    private static final ImportPreviewDatabaseSnapshot.ClusterSnapshot CLUSTER_A1 =
            new ImportPreviewDatabaseSnapshot.ClusterSnapshot(101L, 10L, "A1", "Source A1", "Wave A1");
    private static final ImportPreviewDatabaseSnapshot.ClusterSnapshot CLUSTER_A2 =
            new ImportPreviewDatabaseSnapshot.ClusterSnapshot(102L, 10L, "A2", "Source A2", "Wave A2");
    private static final ImportPreviewDatabaseSnapshot.ClusterSnapshot CLUSTER_B1 =
            new ImportPreviewDatabaseSnapshot.ClusterSnapshot(201L, 20L, "B1", "Source B1", "Wave B1");

    private final ImportPreviewPlanner planner = new ImportPreviewPlanner();

    @Test
    void emergencyRetiresAllAndInsertsAllWithoutOldToNewMatching() {
        var draftRace = draftRace(10L, "A");
        var old = registration(1, "10", draftRace, null, DOB, "Same", 1_000);
        ImportPreviewPlan plan = emergencyPlan(
                List.of(10L),
                List.of(row(2, "10", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "Same")),
                List.of(old),
                List.of(new ImportPreviewDatabaseSnapshot.ActiveIssueSnapshot(99L, old.id(), 10L))
        );

        assertThat(plan.blockingErrorsPresent()).isFalse();
        assertThat(plan.rows()).singleElement().satisfies(planned -> {
            assertThat(planned.decision()).isEqualTo(ImportPreviewDecision.NEW);
            assertThat(planned.futureAction()).isEqualTo(ImportPreviewAction.INSERT);
            assertThat(planned.matchedRegistrationId()).isNull();
        });
        assertThat(plan.modeSummary()).isEqualTo(new ImportPreviewResponseDto.ModeSummary(1, 0, 0, 0, 0, 0));
        assertThat(plan.emergencySummary().dangerousOperation()).isTrue();
        assertThat(plan.emergencySummary().races()).singleElement().satisfies(summary -> {
            assertThat(summary.currentCount()).isOne();
            assertThat(summary.sourceCount()).isOne();
            assertThat(summary.wouldRetireCount()).isOne();
            assertThat(summary.wouldInsertCount()).isOne();
            assertThat(summary.activeIssuesWouldArchiveCount()).isOne();
        });
    }

    @Test
    void emergencyBlocksBothDirectionsOfCrossScopeBibMovementAndEmptyCoverage() {
        var raceA = draftRace(10L, "A");
        var raceB = draftRace(20L, "B");
        var inScope = registration(1, "IN", raceA, null, DOB, "Inside", 1_000);
        var outside = registration(2, "OUT", raceB, null, DOB, "Outside", 2_000);
        ImportPreviewDatabaseSnapshot snapshot = emergencySnapshot(
                List.of(raceA, raceB), List.of(inScope, outside), List.of()
        );
        ImportPreviewPlan plan = planner.plan(
                EVENT_ID, ImportOperationMode.EMERGENCY_REPLACE, List.of(10L), FILE_SHA, "cross.csv",
                new TimingCsvParseResult(2, List.of(
                        row(2, "OUT", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "Collision"),
                        row(3, "IN", "B", SourceField.value(DOB), SourceField.absent(), 2_000, "Moved")
                ), List.of()),
                List.of(), snapshot
        );

        assertThat(plan.rows()).allSatisfy(row -> {
            assertThat(row.reasonCode()).isEqualTo("CROSS_SCOPE_BIB_CONFLICT");
            assertThat(row.futureAction()).isEqualTo(ImportPreviewAction.BLOCKED);
        });
        assertThat(plan.diagnostics()).anySatisfy(diagnostic ->
                assertThat(diagnostic.code()).isEqualTo("EMPTY_REPLACEMENT_SCOPE"));
        assertThat(plan.blockingErrorsPresent()).isTrue();
    }

    @Test
    void emergencyDigestChangesWhenActiveIssueSetChanges() {
        var race = draftRace(10L, "A");
        var old = registration(1, "10", race, null, DOB, "Old", 1_000);
        List<TimingResultImportRow> rows = List.of(
                row(2, "20", "A", SourceField.value(DOB), SourceField.absent(), 2_000, "New")
        );
        ImportPreviewPlan before = emergencyPlan(List.of(10L), rows, List.of(old), List.of());
        ImportPreviewPlan after = emergencyPlan(
                List.of(10L), rows, List.of(old),
                List.of(new ImportPreviewDatabaseSnapshot.ActiveIssueSnapshot(5L, old.id(), 10L))
        );

        assertThat(before.planDigest()).isNotEqualTo(after.planDigest());
        assertThat(before.emergencySummary().totals().activeIssuesArchiveCount()).isZero();
        assertThat(after.emergencySummary().totals().activeIssuesArchiveCount()).isOne();
    }

    @Test
    void classifiesMatchingWithoutTreatingBibOrNameAsIdentity() {
        List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot> registrations = List.of(
                registration(1, "10", RACE_A, null, DOB, "Ivan", 1_000),
                registration(2, "11", RACE_A, null, DOB, "Ivan", 1_000),
                registration(3, "12", RACE_A, null, DOB, "First", 1_000),
                registration(4, "12", RACE_B, null, DOB.plusDays(1), "Second", 1_000)
        );
        List<TimingResultImportRow> rows = List.of(
                row(2, "20", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "Ivan"),
                row(3, "10", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "Ivan"),
                row(4, "11", "A", SourceField.value(DOB), SourceField.absent(), 1_100, "Ivan"),
                row(5, "12", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "First"),
                row(6, " ", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "Ivan"),
                row(7, "99", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "Ivan")
        );

        ImportPreviewPlan add = plan(ImportOperationMode.ADD_NEW, List.of(10L, 20L), rows, registrations);

        assertThat(add.totals()).isEqualTo(new ImportPreviewResponseDto.Totals(
                6, 6, 2, 1, 1, 1, 0, 1, 0, 0
        ));
        assertThat(find(add, 2).decision()).isEqualTo(ImportPreviewDecision.NEW);
        assertThat(find(add, 2).futureAction()).isEqualTo(ImportPreviewAction.INSERT);
        assertThat(find(add, 3).decision()).isEqualTo(ImportPreviewDecision.EXISTING_UNCHANGED);
        assertThat(find(add, 4).decision()).isEqualTo(ImportPreviewDecision.EXISTING_CHANGED);
        assertThat(find(add, 4).diffs()).extracting(ImportPreviewResponseDto.FieldDiff::field)
                .containsExactly("gunTimeMs");
        assertThat(find(add, 4).futureAction()).isEqualTo(ImportPreviewAction.SKIP);
        assertThat(find(add, 5).decision()).isEqualTo(ImportPreviewDecision.AMBIGUOUS);
        assertThat(find(add, 6).decision()).isEqualTo(ImportPreviewDecision.INVALID);
        assertThat(find(add, 7).decision()).isEqualTo(ImportPreviewDecision.NEW);
        assertThat(add.blockingErrorsPresent()).isTrue();

        ImportPreviewPlan update = plan(ImportOperationMode.UPDATE_EXISTING, List.of(10L, 20L), rows, registrations);
        assertThat(find(update, 4).futureAction()).isEqualTo(ImportPreviewAction.UPDATE);
        assertThat(find(update, 2).futureAction()).isEqualTo(ImportPreviewAction.SKIP);
        assertThat(add.modeSummary()).isEqualTo(new ImportPreviewResponseDto.ModeSummary(2, 0, 2, 0, 1, 2));
        assertThat(update.modeSummary()).isEqualTo(new ImportPreviewResponseDto.ModeSummary(0, 1, 0, 2, 1, 2));
    }

    @Test
    void duplicateBibIsEventWideBlockingButLeadingZerosRemainDistinct() {
        ImportPreviewPlan sameRace = plan(ImportOperationMode.ADD_NEW, List.of(10L, 20L), List.of(
                row(2, " 1100 ", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "One"),
                row(3, "1100", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "Two")
        ), List.of());
        assertThat(sameRace.totals().duplicateCount()).isEqualTo(2);
        assertThat(sameRace.duplicateBibs()).singleElement().satisfies(duplicate -> {
            assertThat(duplicate.bib()).isEqualTo("1100");
            assertThat(duplicate.rows()).extracting(ImportPreviewResponseDto.DuplicateBibRow::sourceRowNumber)
                    .containsExactly(2, 3);
        });
        assertThat(sameRace.blockingErrorsPresent()).isTrue();

        ImportPreviewPlan differentRaces = plan(ImportOperationMode.ADD_NEW, List.of(10L, 20L), List.of(
                row(2, "X", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "One"),
                row(3, "X", "B", SourceField.value(DOB), SourceField.absent(), 1_000, "Two")
        ), List.of());
        assertThat(differentRaces.totals().duplicateCount()).isEqualTo(2);

        ImportPreviewPlan leadingZeros = plan(ImportOperationMode.ADD_NEW, List.of(10L), List.of(
                row(2, "0010", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "One"),
                row(3, "10", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "Two")
        ), List.of());
        assertThat(leadingZeros.totals().duplicateCount()).isZero();
        assertThat(leadingZeros.totals().newCount()).isEqualTo(2);
    }

    @Test
    void raceMoveRequiresUniqueCandidateMatchingDobAndBothRacesInScope() {
        var current = registration(1, "50", RACE_A, null, DOB, "Ivan", 1_000);

        ImportPreviewPlan safe = plan(ImportOperationMode.UPDATE_EXISTING, List.of(10L, 20L), List.of(
                row(2, "50", "B", SourceField.value(DOB), SourceField.absent(), 1_000, "Renamed")
        ), List.of(current));
        assertThat(find(safe, 2).decision()).isEqualTo(ImportPreviewDecision.EXISTING_CHANGED);
        assertThat(find(safe, 2).diffs()).extracting(ImportPreviewResponseDto.FieldDiff::field)
                .contains("race", "firstName", "displayName");

        assertConflict(plan(ImportOperationMode.UPDATE_EXISTING, List.of(10L, 20L), List.of(
                row(2, "50", "B", SourceField.value(DOB.plusDays(1)), SourceField.absent(), 1_000, "Ivan")
        ), List.of(current)), "RACE_MOVE_DOB_MISMATCH");
        assertConflict(plan(ImportOperationMode.UPDATE_EXISTING, List.of(10L, 20L), List.of(
                row(2, "50", "B", SourceField.absent(), SourceField.absent(), 1_000, "Ivan")
        ), List.of(current)), "RACE_MOVE_REQUIRES_DOB");
        assertConflict(plan(ImportOperationMode.UPDATE_EXISTING, List.of(10L, 20L), List.of(
                row(2, "50", "B", SourceField.empty(), SourceField.absent(), 1_000, "Ivan")
        ), List.of(current)), "RACE_MOVE_REQUIRES_DOB");
        assertConflict(plan(ImportOperationMode.UPDATE_EXISTING, List.of(20L), List.of(
                row(2, "50", "B", SourceField.value(DOB), SourceField.absent(), 1_000, "Ivan")
        ), List.of(current)), "CURRENT_RACE_OUT_OF_SCOPE");

        ImportPreviewPlan targetOutside = plan(ImportOperationMode.UPDATE_EXISTING, List.of(10L), List.of(
                row(2, "50", "B", SourceField.value(DOB), SourceField.absent(), 1_000, "Ivan")
        ), List.of(current));
        assertThat(find(targetOutside, 2).decision()).isEqualTo(ImportPreviewDecision.CONFLICT);
        assertThat(find(targetOutside, 2).reasonCode()).isEqualTo("TARGET_RACE_OUT_OF_SCOPE");
        assertThat(targetOutside.totals().conflictCount()).isOne();

        var missingDob = registration(2, "51", RACE_A, null, null, "Ivan", 1_000);
        assertConflict(plan(ImportOperationMode.UPDATE_EXISTING, List.of(10L, 20L), List.of(
                row(2, "51", "B", SourceField.value(DOB), SourceField.absent(), 1_000, "Ivan")
        ), List.of(missingDob)), "RACE_MOVE_DOB_MISMATCH");
    }

    @Test
    void resolvesClusterExactlyAndKeepsAbsentDifferentFromEmpty() {
        var current = registration(1, "60", RACE_A, CLUSTER_A1, DOB, "Ivan", 1_000);

        ImportPreviewPlan changed = planWithClusters(List.of(
                row(2, "60", "A", SourceField.value(DOB), SourceField.value("A2"), 1_000, "Ivan")
        ), List.of(current));
        assertThat(find(changed, 2).decision()).isEqualTo(ImportPreviewDecision.EXISTING_CHANGED);
        assertThat(find(changed, 2).diffs()).extracting(ImportPreviewResponseDto.FieldDiff::field)
                .containsExactly("cluster");

        ImportPreviewPlan absent = planWithClusters(List.of(
                row(2, "60", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "Ivan")
        ), List.of(current));
        assertThat(find(absent, 2).decision()).isEqualTo(ImportPreviewDecision.EXISTING_UNCHANGED);

        assertConflict(planWithClusters(List.of(
                row(2, "60", "A", SourceField.value(DOB), SourceField.empty(), 1_000, "Ivan")
        ), List.of(current)), "START_CLUSTER_CLEAR_NOT_ALLOWED");
        assertConflict(planWithClusters(List.of(
                row(2, "60", "A", SourceField.value(DOB), SourceField.value("UNKNOWN"), 1_000, "Ivan")
        ), List.of(current)), "UNKNOWN_START_CLUSTER");
        assertConflict(planWithClusters(List.of(
                row(2, "60", "A", SourceField.value(DOB), SourceField.value("B1"), 1_000, "Ivan")
        ), List.of(current)), "CROSS_RACE_START_CLUSTER");

        ImportPreviewPlan validNew = planWithClusters(List.of(
                row(2, "NEW", "A", SourceField.value(DOB), SourceField.value("A1"), 1_000, "Ivan")
        ), List.of());
        assertThat(find(validNew, 2).decision()).isEqualTo(ImportPreviewDecision.NEW);

        ImportPreviewPlan byDisplayName = planWithClusters(List.of(
                rowWithClusterFields(
                        2, "DISPLAY", "A", SourceField.absent(), SourceField.value("Wave A1"), SourceField.absent()
                )
        ), List.of());
        assertThat(find(byDisplayName, 2).decision()).isEqualTo(ImportPreviewDecision.NEW);
        ImportPreviewPlan bySourceName = planWithClusters(List.of(
                rowWithClusterFields(
                        2, "SOURCE", "A", SourceField.absent(), SourceField.absent(), SourceField.value("Source A1")
                )
        ), List.of());
        assertThat(find(bySourceName, 2).decision()).isEqualTo(ImportPreviewDecision.NEW);

        var duplicateCode = new ImportPreviewDatabaseSnapshot.ClusterSnapshot(
                103L, 10L, "A1", "Other source", "Other wave"
        );
        ImportPreviewPlan ambiguousCluster = planner.plan(
                EVENT_ID, ImportOperationMode.UPDATE_EXISTING, List.of(10L), FILE_SHA,
                new TimingCsvParseResult(1, List.of(
                        row(2, "AMB", "A", SourceField.value(DOB), SourceField.value("A1"), 1_000, "Ivan")
                ), List.of()),
                List.of(),
                snapshot(7, List.of(RACE_A), List.of(CLUSTER_A1, duplicateCode), List.of())
        );
        assertConflict(ambiguousCluster, "AMBIGUOUS_START_CLUSTER");
    }

    @Test
    void raceMoveRequiresReplacementForAnOldRaceScopedCluster() {
        var current = registration(1, "70", RACE_A, CLUSTER_A1, DOB, "Ivan", 1_000);

        ImportPreviewPlan replaced = planWithClusters(List.of(
                row(2, "70", "B", SourceField.value(DOB), SourceField.value("B1"), 1_000, "Ivan")
        ), List.of(current));
        assertThat(find(replaced, 2).decision()).isEqualTo(ImportPreviewDecision.EXISTING_CHANGED);
        assertThat(find(replaced, 2).diffs()).extracting(ImportPreviewResponseDto.FieldDiff::field)
                .contains("race", "cluster");

        assertConflict(planWithClusters(List.of(
                row(2, "70", "B", SourceField.value(DOB), SourceField.absent(), 1_000, "Ivan")
        ), List.of(current)), "RACE_MOVE_CLUSTER_REQUIRED");
    }

    @Test
    void scopeExcludesOtherRaceRowsFromApplicableTotals() {
        ImportPreviewPlan result = plan(ImportOperationMode.UPDATE_EXISTING, List.of(10L), List.of(
                row(2, "A-1", "A", SourceField.value(DOB), SourceField.absent(), 1_000, "One"),
                row(3, "B-1", "B", SourceField.value(DOB), SourceField.absent(), 1_000, "Two")
        ), List.of());

        assertThat(result.totals()).isEqualTo(new ImportPreviewResponseDto.Totals(
                2, 1, 1, 0, 0, 0, 0, 0, 0, 1
        ));
        assertThat(find(result, 3).decision()).isEqualTo(ImportPreviewDecision.OUT_OF_SCOPE);
        assertThat(result.modeSummary().newSkipped()).isOne();
    }

    @Test
    void digestIsDeterministicAndIncludesModeScopeAndCurrentState() {
        TimingResultImportRow source = row(
                2, "80", "A", SourceField.value(DOB), SourceField.absent(), 1_100, "Ivan"
        );
        var current = registration(1, "80", RACE_A, null, DOB, "Ivan", 1_000);

        ImportPreviewPlan first = plan(ImportOperationMode.UPDATE_EXISTING, List.of(20L, 10L),
                List.of(source), List.of(current));
        ImportPreviewPlan repeated = plan(ImportOperationMode.UPDATE_EXISTING, List.of(10L, 20L),
                List.of(source), List.of(current));
        ImportPreviewPlan anotherMode = plan(ImportOperationMode.ADD_NEW, List.of(10L, 20L),
                List.of(source), List.of(current));
        ImportPreviewPlan anotherScope = plan(ImportOperationMode.UPDATE_EXISTING, List.of(10L),
                List.of(source), List.of(current));
        var changedCurrent = registration(1, "80", RACE_A, null, DOB, "Ivan", 900);
        ImportPreviewPlan anotherCurrentState = plan(ImportOperationMode.UPDATE_EXISTING, List.of(10L, 20L),
                List.of(source), List.of(changedCurrent));
        ImportPreviewPlan anotherRevision = planner.plan(
                EVENT_ID, ImportOperationMode.UPDATE_EXISTING, List.of(10L, 20L), FILE_SHA,
                new TimingCsvParseResult(1, List.of(source), List.of()), List.of(),
                snapshot(8, List.of(RACE_A, RACE_B), List.of(), List.of(current))
        );

        assertThat(first.planDigest()).isEqualTo(repeated.planDigest());
        assertThat(first.planDigest()).isNotEqualTo(anotherMode.planDigest());
        assertThat(first.planDigest()).isNotEqualTo(anotherScope.planDigest());
        assertThat(first.planDigest()).isNotEqualTo(anotherCurrentState.planDigest());
        assertThat(first.planDigest()).isNotEqualTo(anotherRevision.planDigest());
    }

    @Test
    void detectsSyntheticHeroDuplicateAndKeepsSyntheticM52DuplicateFree() throws Exception {
        TimingCsvParser parser = new TimingCsvParser();
        ImportValidator validator = new ImportValidator();
        TimingCsvParseResult hero = parser.parse(sample("results_gonka2026.csv"));
        List<ImportPreviewDatabaseSnapshot.RaceSnapshot> heroRaces = List.of(
                race(1, "mass"), race(2, "teams"), race(3, "corp"), race(4, "champ")
        );
        ImportPreviewPlan heroPlan = planner.plan(
                EVENT_ID, ImportOperationMode.ADD_NEW, List.of(1L, 2L, 3L, 4L), FILE_SHA,
                hero, validator.validate(hero.rows()), snapshot(0, heroRaces, List.of(), List.of())
        );

        assertThat(heroPlan.duplicateBibs()).filteredOn(duplicate -> duplicate.bib().equals("SYN-DUP"))
                .singleElement().satisfies(duplicate -> {
                    assertThat(duplicate.rows()).extracting(ImportPreviewResponseDto.DuplicateBibRow::sourceRowNumber)
                            .containsExactly(4, 7);
                    assertThat(duplicate.rows()).extracting(ImportPreviewResponseDto.DuplicateBibRow::participantName)
                            .containsExactly("Дубль-А Синтетический", "Дубль-Б Синтетический");
                });
        assertThat(heroPlan.blockingErrorsPresent()).isTrue();

        TimingCsvParseResult m52 = parser.parse(sample("results_m52_2025.csv"));
        List<ImportPreviewDatabaseSnapshot.RaceSnapshot> m52Races = List.of(race(1, "10 km"), race(2, "42.2 km"));
        ImportPreviewPlan m52Plan = planner.plan(
                EVENT_ID, ImportOperationMode.ADD_NEW, List.of(1L, 2L), FILE_SHA,
                m52, validator.validate(m52.rows()), snapshot(0, m52Races, List.of(), List.of())
        );
        assertThat(m52Plan.duplicateBibs()).isEmpty();
        assertThat(m52Plan.blockingErrorsPresent()).isFalse();
        assertThat(m52Plan.totals().newCount()).isEqualTo(31);
    }

    @Test
    void fileLevelValidationDiagnosticBlocksAnEmptyPlan() {
        ImportPreviewPlan result = planner.plan(
                EVENT_ID,
                ImportOperationMode.ADD_NEW,
                List.of(10L),
                FILE_SHA,
                new TimingCsvParseResult(0, List.of(), List.of()),
                new ImportValidator().validate(List.of()),
                snapshot(0, List.of(RACE_A), List.of(), List.of())
        );

        assertThat(result.blockingErrorsPresent()).isTrue();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.sourceRowNumber()).isNull();
            assertThat(diagnostic.field()).isEqualTo("file");
        });
    }

    private ImportPreviewPlan plan(
            ImportOperationMode mode,
            List<Long> scope,
            List<TimingResultImportRow> rows,
            List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot> registrations
    ) {
        return planner.plan(
                EVENT_ID, mode, scope, FILE_SHA,
                new TimingCsvParseResult(rows.size(), rows, List.of()),
                List.of(),
                snapshot(7, List.of(RACE_A, RACE_B), List.of(), registrations)
        );
    }

    private ImportPreviewPlan planWithClusters(
            List<TimingResultImportRow> rows,
            List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot> registrations
    ) {
        return planner.plan(
                EVENT_ID, ImportOperationMode.UPDATE_EXISTING, List.of(10L, 20L), FILE_SHA,
                new TimingCsvParseResult(rows.size(), rows, List.of()),
                List.of(),
                snapshot(7, List.of(RACE_A, RACE_B), List.of(CLUSTER_A1, CLUSTER_A2, CLUSTER_B1), registrations)
        );
    }

    private ImportPreviewPlan emergencyPlan(
            List<Long> scope,
            List<TimingResultImportRow> rows,
            List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot> registrations,
            List<ImportPreviewDatabaseSnapshot.ActiveIssueSnapshot> issues
    ) {
        List<ImportPreviewDatabaseSnapshot.RaceSnapshot> races = registrations.stream()
                .map(ImportPreviewDatabaseSnapshot.RegistrationSnapshot::race)
                .distinct()
                .toList();
        return planner.plan(
                EVENT_ID, ImportOperationMode.EMERGENCY_REPLACE, scope, FILE_SHA, "correct.csv",
                new TimingCsvParseResult(rows.size(), rows, List.of()), List.of(),
                emergencySnapshot(races, registrations, issues)
        );
    }

    private static ImportPreviewDatabaseSnapshot emergencySnapshot(
            List<ImportPreviewDatabaseSnapshot.RaceSnapshot> races,
            List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot> registrations,
            List<ImportPreviewDatabaseSnapshot.ActiveIssueSnapshot> issues
    ) {
        return new ImportPreviewDatabaseSnapshot(
                7, races, List.of(), registrations,
                Instant.parse("2026-09-01T05:00:00Z"), "Asia/Yekaterinburg", List.of(),
                "Test Event", "Kazan", issues
        );
    }

    private static ImportPreviewDatabaseSnapshot.RaceSnapshot draftRace(long id, String sourceCode) {
        return new ImportPreviewDatabaseSnapshot.RaceSnapshot(
                id, sourceCode, sourceCode, id + 100, sourceCode + " format",
                AgeCalculationMode.EVENT_DATE, ResultsPublicationStatus.DRAFT
        );
    }

    private static ImportPreviewDatabaseSnapshot snapshot(
            long revision,
            List<ImportPreviewDatabaseSnapshot.RaceSnapshot> races,
            List<ImportPreviewDatabaseSnapshot.ClusterSnapshot> clusters,
            List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot> registrations
    ) {
        return new ImportPreviewDatabaseSnapshot(
                revision,
                races,
                clusters,
                registrations,
                Instant.parse("2026-09-01T05:00:00Z"),
                "Asia/Yekaterinburg",
                List.of(),
                "Test Event",
                "Kazan",
                List.of()
        );
    }

    private static ImportPreviewDatabaseSnapshot.RegistrationSnapshot registration(
            long id,
            String bib,
            ImportPreviewDatabaseSnapshot.RaceSnapshot race,
            ImportPreviewDatabaseSnapshot.ClusterSnapshot cluster,
            LocalDate birthDate,
            String firstName,
            long gunTimeMs
    ) {
        return new ImportPreviewDatabaseSnapshot.RegistrationSnapshot(
                id, bib, firstName + " Runner", firstName, "Runner", "male", birthDate, "Open",
                race, cluster,
                new ImportPreviewDatabaseSnapshot.ResultSnapshot(
                        id + 1_000, "finished", Duration.ofMillis(gunTimeMs), Duration.ofMillis(900),
                        1, 1, 1, 1, 1, 1
                )
        );
    }

    private static TimingResultImportRow row(
            int sourceRowNumber,
            String bib,
            String raceCode,
            SourceField<LocalDate> birthDate,
            SourceField<String> clusterCode,
            long gunTimeMs,
            String firstName
    ) {
        return new TimingResultImportRow(
                sourceRowNumber,
                Integer.toHexString(sourceRowNumber).repeat(64).substring(0, 64),
                SourceField.value(firstName),
                SourceField.value("Runner"),
                SourceField.value("male"),
                birthDate,
                raceCode,
                null,
                null,
                null,
                bib,
                SourceField.value("Open"),
                clusterCode,
                SourceField.absent(),
                SourceField.absent(),
                "finished",
                RegistrationEntryKind.UNKNOWN,
                SourceField.value(Duration.ofMillis(gunTimeMs)),
                SourceField.value(Duration.ofMillis(900)),
                SourceField.value(1),
                SourceField.value(1),
                SourceField.value(1),
                SourceField.value(1),
                SourceField.value(1),
                SourceField.value(1)
        );
    }

    private static TimingResultImportRow rowWithClusterFields(
            int sourceRowNumber,
            String bib,
            String raceCode,
            SourceField<String> clusterCode,
            SourceField<String> clusterName,
            SourceField<String> clusterSourceName
    ) {
        TimingResultImportRow base = row(
                sourceRowNumber, bib, raceCode, SourceField.value(DOB), clusterCode, 1_000, "Ivan"
        );
        return new TimingResultImportRow(
                base.sourceRowNumber(), base.sourceRowHash(),
                base.firstNameSource(), base.lastNameSource(), base.genderSource(), base.birthDateSource(),
                base.raceCode(), base.sportFormatCode(), base.sportFormatSourceName(), base.sportFormatDisplayName(),
                base.bib(), base.categorySource(), clusterCode, clusterName, clusterSourceName,
                base.status(), base.entryKind(), base.gunTimeSource(), base.chipTimeSource(),
                base.overallPlaceSource(), base.genderPlaceSource(), base.categoryPlaceSource(),
                base.netOverallPlaceSource(), base.netGenderPlaceSource(), base.netCategoryPlaceSource()
        );
    }

    private static ImportPreviewDatabaseSnapshot.RaceSnapshot race(long id, String sourceCode) {
        return new ImportPreviewDatabaseSnapshot.RaceSnapshot(
                id, sourceCode, sourceCode, id + 100, sourceCode + " format"
        );
    }

    private static ImportPreviewResponseDto.Row find(ImportPreviewPlan plan, int sourceRowNumber) {
        return plan.rows().stream()
                .filter(row -> row.sourceRowNumber() == sourceRowNumber)
                .findFirst()
                .orElseThrow();
    }

    private static void assertConflict(ImportPreviewPlan plan, String reasonCode) {
        assertThat(plan.rows()).singleElement().satisfies(row -> {
            assertThat(row.decision()).isEqualTo(ImportPreviewDecision.CONFLICT);
            assertThat(row.futureAction()).isEqualTo(ImportPreviewAction.BLOCKED);
            assertThat(row.reasonCode()).isEqualTo(reasonCode);
        });
    }

    private static Path sample(String filename) {
        return Path.of("..", "samples", filename).toAbsolutePath().normalize();
    }
}
