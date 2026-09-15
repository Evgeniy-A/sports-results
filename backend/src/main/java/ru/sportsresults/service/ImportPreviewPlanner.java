package ru.sportsresults.service;

import org.springframework.stereotype.Component;
import ru.sportsresults.api.dto.ImportErrorDto;
import ru.sportsresults.api.dto.ImportPreviewResponseDto;
import ru.sportsresults.domain.ImportOperationMode;
import ru.sportsresults.importing.ImportPreviewAction;
import ru.sportsresults.importing.ImportPreviewDecision;
import ru.sportsresults.importing.SourceField;
import ru.sportsresults.importing.SourceFieldState;
import ru.sportsresults.importing.TimingCsvParseResult;
import ru.sportsresults.importing.TimingCsvRowError;
import ru.sportsresults.importing.TimingResultImportRow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@Component
public class ImportPreviewPlanner {

    public ImportPreviewPlan plan(
            Long eventId,
            ImportOperationMode mode,
            List<Long> scopeRaceIds,
            String fileSha256,
            TimingCsvParseResult parsed,
            List<ImportErrorDto> validationErrors,
            ImportPreviewDatabaseSnapshot snapshot
    ) {
        return plan(eventId, mode, scopeRaceIds, fileSha256, null, parsed, validationErrors, snapshot);
    }

    public ImportPreviewPlan plan(
            Long eventId,
            ImportOperationMode mode,
            List<Long> scopeRaceIds,
            String fileSha256,
            String sourceFilename,
            TimingCsvParseResult parsed,
            List<ImportErrorDto> validationErrors,
            ImportPreviewDatabaseSnapshot snapshot
    ) {
        Set<Long> scope = new LinkedHashSet<>(scopeRaceIds);
        Map<String, ImportPreviewDatabaseSnapshot.RaceSnapshot> racesBySourceCode = snapshot.races().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ImportPreviewDatabaseSnapshot.RaceSnapshot::sourceCode,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<String, List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot>> registrationsByBib = new LinkedHashMap<>();
        for (ImportPreviewDatabaseSnapshot.RegistrationSnapshot registration : snapshot.registrations()) {
            String bib = normalizeBib(registration.bib());
            if (bib != null) {
                registrationsByBib.computeIfAbsent(bib, ignored -> new ArrayList<>()).add(registration);
            }
        }

        DiagnosticAnalysis diagnosticAnalysis = diagnostics(parsed, validationErrors);
        DuplicateAnalysis duplicateAnalysis = duplicates(parsed.rows(), racesBySourceCode);
        List<ImportPreviewResponseDto.Row> plannedRows = new ArrayList<>(parsed.rows().size());
        for (TimingResultImportRow row : parsed.rows()) {
            plannedRows.add(mode == ImportOperationMode.EMERGENCY_REPLACE ? classifyEmergency(
                    row,
                    scope,
                    racesBySourceCode,
                    registrationsByBib,
                    snapshot,
                    diagnosticAnalysis.byRow().getOrDefault(row.sourceRowNumber(), List.of()),
                    duplicateAnalysis.duplicateBibs()
            ) : classify(
                    row,
                    mode,
                    scope,
                    racesBySourceCode,
                    registrationsByBib,
                    snapshot,
                    diagnosticAnalysis.byRow().getOrDefault(row.sourceRowNumber(), List.of()),
                    duplicateAnalysis.duplicateBibs()
            ));
        }
        plannedRows.sort(Comparator.comparingInt(ImportPreviewResponseDto.Row::sourceRowNumber));

        ImportPreviewResponseDto.Totals totals = totals(parsed.totalRows(), plannedRows);
        ImportPreviewResponseDto.ModeSummary modeSummary = modeSummary(mode, totals);
        boolean blocking = totals.ambiguousCount() > 0
                || totals.conflictCount() > 0
                || totals.invalidCount() > 0
                || !duplicateAnalysis.diagnostics().isEmpty()
                || diagnosticAnalysis.all().stream().anyMatch(diagnostic -> diagnostic.sourceRowNumber() == null);
        List<ImportPreviewResponseDto.Diagnostic> combinedDiagnostics = new ArrayList<>(diagnosticAnalysis.all());
        if (mode == ImportOperationMode.EMERGENCY_REPLACE) {
            for (Long raceId : scopeRaceIds) {
                long currentCount = snapshot.registrations().stream()
                        .filter(registration -> registration.race().id().equals(raceId)).count();
                long validSourceCount = plannedRows.stream()
                        .filter(row -> row.targetRace() != null && row.targetRace().raceId().equals(raceId))
                        .filter(row -> row.decision() == ImportPreviewDecision.NEW)
                        .count();
                if (currentCount > 0 && validSourceCount == 0) {
                    combinedDiagnostics.add(new ImportPreviewResponseDto.Diagnostic(
                            null, "raceIds", "EMPTY_REPLACEMENT_SCOPE",
                            "Selected Race " + raceId + " has current data but no valid replacement rows"
                    ));
                }
            }
        }
        List<ImportPreviewResponseDto.Diagnostic> allDiagnostics = combinedDiagnostics.stream()
                .sorted(diagnosticOrder())
                .toList();
        blocking = blocking || allDiagnostics.stream().anyMatch(diagnostic -> diagnostic.sourceRowNumber() == null);
        ImportPreviewResponseDto.EmergencySummary emergencySummary = mode == ImportOperationMode.EMERGENCY_REPLACE
                ? emergencySummary(sourceFilename, fileSha256, scopeRaceIds, snapshot, plannedRows, totals, allDiagnostics)
                : null;
        String digest = digest(
                eventId, mode, scopeRaceIds, fileSha256, snapshot.resultDataRevision(),
                plannedRows, duplicateAnalysis.diagnostics(), allDiagnostics, snapshot, emergencySummary
        );
        return new ImportPreviewPlan(
                totals,
                modeSummary,
                blocking,
                plannedRows,
                duplicateAnalysis.diagnostics(),
                allDiagnostics,
                digest,
                emergencySummary
        );
    }

    private static ImportPreviewResponseDto.Row classifyEmergency(
            TimingResultImportRow row,
            Set<Long> scope,
            Map<String, ImportPreviewDatabaseSnapshot.RaceSnapshot> racesBySourceCode,
            Map<String, List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot>> registrationsByBib,
            ImportPreviewDatabaseSnapshot snapshot,
            List<ImportPreviewResponseDto.Diagnostic> rowErrors,
            Set<String> duplicateBibs
    ) {
        String bib = normalizeBib(row.bib());
        String participantName = ImportDisplayName.from(row);
        ImportPreviewDatabaseSnapshot.RaceSnapshot targetRace = racesBySourceCode.get(row.raceCode());
        ImportPreviewResponseDto.RaceRef targetRaceRef = raceRef(targetRace);
        if (!rowErrors.isEmpty()) {
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.INVALID, ImportPreviewAction.BLOCKED,
                    "INVALID_SOURCE_ROW", "Source row contains invalid values", List.of());
        }
        if (bib == null) {
            return row(row, null, participantName, targetRaceRef, null,
                    ImportPreviewDecision.INVALID, ImportPreviewAction.BLOCKED,
                    "EMPTY_BIB", "A usable bib is required", List.of());
        }
        if (targetRace == null) {
            return row(row, bib, participantName, null, null,
                    ImportPreviewDecision.INVALID, ImportPreviewAction.BLOCKED,
                    "UNKNOWN_RACE", "Source race is not configured for this event", List.of());
        }
        if (duplicateBibs.contains(bib)) {
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.DUPLICATE_IN_FILE, ImportPreviewAction.BLOCKED,
                    "DUPLICATE_BIB_IN_FILE", "Bib occurs more than once in the source event", List.of());
        }
        List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot> candidates =
                registrationsByBib.getOrDefault(bib, List.of());
        if (!scope.contains(targetRace.id())) {
            if (candidates.stream().anyMatch(candidate -> scope.contains(candidate.race().id()))) {
                return row(row, bib, participantName, targetRaceRef, null,
                        ImportPreviewDecision.CONFLICT, ImportPreviewAction.BLOCKED,
                        "CROSS_SCOPE_BIB_CONFLICT",
                        "Out-of-scope source bib belongs to a current registration in replacement scope", List.of());
            }
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.OUT_OF_SCOPE, ImportPreviewAction.SKIP,
                    "TARGET_RACE_OUT_OF_SCOPE", "Source row target race is outside the selected scope", List.of());
        }
        if (candidates.stream().anyMatch(candidate -> !scope.contains(candidate.race().id()))) {
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.CONFLICT, ImportPreviewAction.BLOCKED,
                    "CROSS_SCOPE_BIB_CONFLICT",
                    "Replacement bib belongs to a current registration outside the selected scope", List.of());
        }
        ClusterResolution cluster = resolveCluster(row, targetRace, snapshot.clusters());
        if (cluster.errorCode() != null) {
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.CONFLICT, ImportPreviewAction.BLOCKED,
                    cluster.errorCode(), cluster.errorMessage(), List.of());
        }
        CategoryBlock categoryBlock = categoryBlock(
                row.birthDate(), row.gender(), row.category(), row, targetRace, snapshot
        );
        if (categoryBlock != null) {
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.INVALID, ImportPreviewAction.BLOCKED,
                    categoryBlock.code(), categoryBlock.message(), List.of());
        }
        List<ImportPreviewResponseDto.FieldDiff> effects = new ArrayList<>(
                categoryDefinitionDiffs(row, targetRace, snapshot.categories())
        );
        if (cluster.cluster() != null) {
            effects.add(diff("cluster", null, displayCluster(cluster.cluster())));
        }
        return row(row, bib, participantName, targetRaceRef, null,
                ImportPreviewDecision.NEW, ImportPreviewAction.INSERT,
                "EMERGENCY_INSERT_NEW", "Source row will become a new current registration", effects);
    }

    private static ImportPreviewResponseDto.Row classify(
            TimingResultImportRow row,
            ImportOperationMode mode,
            Set<Long> scope,
            Map<String, ImportPreviewDatabaseSnapshot.RaceSnapshot> racesBySourceCode,
            Map<String, List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot>> registrationsByBib,
            ImportPreviewDatabaseSnapshot snapshot,
            List<ImportPreviewResponseDto.Diagnostic> rowErrors,
            Set<String> duplicateBibs
    ) {
        String bib = normalizeBib(row.bib());
        String participantName = ImportDisplayName.from(row);
        ImportPreviewDatabaseSnapshot.RaceSnapshot targetRace = racesBySourceCode.get(row.raceCode());
        ImportPreviewResponseDto.RaceRef targetRaceRef = raceRef(targetRace);

        if (!rowErrors.isEmpty()) {
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.INVALID, ImportPreviewAction.BLOCKED,
                    "INVALID_SOURCE_ROW", "Source row contains invalid values", List.of());
        }
        if (bib == null) {
            return row(row, null, participantName, targetRaceRef, null,
                    ImportPreviewDecision.INVALID, ImportPreviewAction.BLOCKED,
                    "EMPTY_BIB", "A usable bib is required for safe matching", List.of());
        }
        if (targetRace == null) {
            return row(row, bib, participantName, null, null,
                    ImportPreviewDecision.INVALID, ImportPreviewAction.BLOCKED,
                    "UNKNOWN_RACE", "Source race is not configured for this event", List.of());
        }
        if (duplicateBibs.contains(bib)) {
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.DUPLICATE_IN_FILE, ImportPreviewAction.BLOCKED,
                    "DUPLICATE_BIB_IN_FILE", "Bib occurs more than once in the source event", List.of());
        }
        List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot> candidates =
                registrationsByBib.getOrDefault(bib, List.of());
        if (!scope.contains(targetRace.id())) {
            if (mode == ImportOperationMode.UPDATE_EXISTING && candidates.size() == 1
                    && scope.contains(candidates.getFirst().race().id())
                    && !candidates.getFirst().race().id().equals(targetRace.id())) {
                return row(row, bib, participantName, targetRaceRef, candidates.getFirst(),
                        ImportPreviewDecision.CONFLICT, ImportPreviewAction.BLOCKED,
                        "TARGET_RACE_OUT_OF_SCOPE", "Target registration race is outside the selected scope",
                        raceDiff(candidates.getFirst().race(), targetRace));
            }
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.OUT_OF_SCOPE, ImportPreviewAction.SKIP,
                    "TARGET_RACE_OUT_OF_SCOPE", "Source row target race is outside the selected scope", List.of());
        }

        ClusterResolution cluster = resolveCluster(row, targetRace, snapshot.clusters());
        if (cluster.errorCode() != null) {
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.CONFLICT, ImportPreviewAction.BLOCKED,
                    cluster.errorCode(), cluster.errorMessage(), List.of());
        }

        if (candidates.isEmpty()) {
            if (mode == ImportOperationMode.ADD_NEW) {
                CategoryBlock categoryBlock = categoryBlock(
                        row.birthDate(), row.gender(), row.category(), row, targetRace, snapshot
                );
                if (categoryBlock != null) {
                    return row(row, bib, participantName, targetRaceRef, null,
                            ImportPreviewDecision.INVALID, ImportPreviewAction.BLOCKED,
                            categoryBlock.code(), categoryBlock.message(), List.of());
                }
            }
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.NEW, action(mode, ImportPreviewDecision.NEW, null),
                    "NEW_REGISTRATION", "No current registration has this event bib",
                    categoryDefinitionDiffs(row, targetRace, snapshot.categories()));
        }
        if (candidates.size() > 1) {
            return row(row, bib, participantName, targetRaceRef, null,
                    ImportPreviewDecision.AMBIGUOUS, ImportPreviewAction.BLOCKED,
                    "AMBIGUOUS_EVENT_BIB", "More than one current registration has this event bib", List.of());
        }

        ImportPreviewDatabaseSnapshot.RegistrationSnapshot candidate = candidates.getFirst();
        boolean raceChanged = !candidate.race().id().equals(targetRace.id());
        if (raceChanged) {
            if (!scope.contains(candidate.race().id())) {
                return row(row, bib, participantName, targetRaceRef, candidate,
                        ImportPreviewDecision.CONFLICT, ImportPreviewAction.BLOCKED,
                        "CURRENT_RACE_OUT_OF_SCOPE", "Current registration race is outside the selected scope",
                        raceDiff(candidate.race(), targetRace));
            }
            if (!row.birthDateSource().hasValue()) {
                return row(row, bib, participantName, targetRaceRef, candidate,
                        ImportPreviewDecision.CONFLICT, ImportPreviewAction.BLOCKED,
                        "RACE_MOVE_REQUIRES_DOB", "Race move requires a non-empty source birth date",
                        raceDiff(candidate.race(), targetRace));
            }
            if (candidate.birthDate() == null || !candidate.birthDate().equals(row.birthDateSource().value())) {
                List<ImportPreviewResponseDto.FieldDiff> conflictDiffs = new ArrayList<>(
                        raceDiff(candidate.race(), targetRace)
                );
                conflictDiffs.add(diff(
                        "birthDate", date(candidate.birthDate()), date(row.birthDateSource().value())
                ));
                return row(row, bib, participantName, targetRaceRef, candidate,
                        ImportPreviewDecision.CONFLICT, ImportPreviewAction.BLOCKED,
                        "RACE_MOVE_DOB_MISMATCH", "Source birth date does not verify the race move", conflictDiffs);
            }
            if (candidate.cluster() != null && cluster.state() == SourceFieldState.ABSENT) {
                return row(row, bib, participantName, targetRaceRef, candidate,
                        ImportPreviewDecision.CONFLICT, ImportPreviewAction.BLOCKED,
                        "RACE_MOVE_CLUSTER_REQUIRED", "The old race cluster cannot be preserved after a race move",
                        raceDiff(candidate.race(), targetRace));
            }
        }
        if (cluster.state() == SourceFieldState.EMPTY && candidate.cluster() != null) {
            return row(row, bib, participantName, targetRaceRef, candidate,
                    ImportPreviewDecision.CONFLICT, ImportPreviewAction.BLOCKED,
                    "START_CLUSTER_CLEAR_NOT_ALLOWED", "Source cluster is empty but the registration has a cluster",
                    List.of(diff("cluster", displayCluster(candidate.cluster()), null)));
        }

        if (mode == ImportOperationMode.UPDATE_EXISTING) {
            CategoryBlock categoryBlock = categoryBlock(
                    apply(candidate.birthDate(), row.birthDateSource()),
                    apply(candidate.gender(), row.genderSource()),
                    apply(candidate.sourceCategory(), row.categorySource()),
                    row,
                    targetRace,
                    snapshot
            );
            if (categoryBlock != null) {
                return row(row, bib, participantName, targetRaceRef, candidate,
                        ImportPreviewDecision.INVALID, ImportPreviewAction.BLOCKED,
                        categoryBlock.code(), categoryBlock.message(), List.of());
            }
        }
        List<ImportPreviewResponseDto.FieldDiff> diffs = differences(row, candidate, targetRace, cluster, snapshot);
        ImportPreviewDecision decision = diffs.isEmpty()
                ? ImportPreviewDecision.EXISTING_UNCHANGED
                : ImportPreviewDecision.EXISTING_CHANGED;
        return row(row, bib, participantName, targetRaceRef, candidate, decision, action(mode, decision, candidate),
                decision == ImportPreviewDecision.EXISTING_CHANGED ? "SAFE_CHANGES_FOUND" : "NO_CHANGES",
                decision == ImportPreviewDecision.EXISTING_CHANGED
                        ? "Safe field changes were detected"
                        : "No meaningful source changes were detected",
                diffs);
    }

    private static List<ImportPreviewResponseDto.FieldDiff> differences(
            TimingResultImportRow row,
            ImportPreviewDatabaseSnapshot.RegistrationSnapshot candidate,
            ImportPreviewDatabaseSnapshot.RaceSnapshot targetRace,
            ClusterResolution cluster,
            ImportPreviewDatabaseSnapshot snapshot
    ) {
        List<ImportPreviewResponseDto.FieldDiff> diffs = new ArrayList<>();
        if (!candidate.race().id().equals(targetRace.id())) {
            diffs.add(diff("race", displayRace(candidate.race()), displayRace(targetRace)));
        }

        addSourceDiff(diffs, "firstName", candidate.firstName(), row.firstNameSource(), ImportPreviewPlanner::text);
        addSourceDiff(diffs, "lastName", candidate.lastName(), row.lastNameSource(), ImportPreviewPlanner::text);
        addSourceDiff(diffs, "gender", candidate.gender(), row.genderSource(), ImportPreviewPlanner::text);
        addSourceDiff(diffs, "birthDate", candidate.birthDate(), row.birthDateSource(), ImportPreviewPlanner::date);
        addSourceDiff(diffs, "sourceCategory", candidate.sourceCategory(), row.categorySource(), ImportPreviewPlanner::text);

        String prospectiveFirstName = apply(candidate.firstName(), row.firstNameSource());
        String prospectiveLastName = apply(candidate.lastName(), row.lastNameSource());
        if (!row.firstNameSource().isAbsent() || !row.lastNameSource().isAbsent()) {
            String prospectiveDisplayName = displayName(prospectiveFirstName, prospectiveLastName, row.bib());
            if (!Objects.equals(candidate.displayName(), prospectiveDisplayName)) {
                diffs.add(diff("displayName", candidate.displayName(), prospectiveDisplayName));
            }
        }

        if (cluster.state() == SourceFieldState.VALUE) {
            String oldCluster = displayCluster(candidate.cluster());
            String newCluster = displayCluster(cluster.cluster());
            if (!Objects.equals(candidate.cluster() == null ? null : candidate.cluster().id(), cluster.cluster().id())) {
                diffs.add(diff("cluster", oldCluster, newCluster));
            }
        }

        boolean sourceCategoryTargetChanged = !candidate.race().id().equals(targetRace.id())
                || !Objects.equals(candidate.sourceCategory(), apply(candidate.sourceCategory(), row.categorySource()));
        ImportPreviewDatabaseSnapshot.CategorySnapshot newCategory = sourceCategoryTargetChanged
                ? missingSourceCategory(row, targetRace, snapshot.categories())
                : null;
        if (newCategory != null) {
            diffs.add(diff("categoryDefinition", null, categoryReference(newCategory)));
        }
        List<ImportPreviewDatabaseSnapshot.CategorySnapshot> prospectiveCategories = new ArrayList<>(
                snapshot.categories()
        );
        if (newCategory != null) prospectiveCategories.add(newCategory);
        String prospectiveGender = apply(candidate.gender(), row.genderSource());
        LocalDate prospectiveBirthDate = apply(candidate.birthDate(), row.birthDateSource());
        String prospectiveSourceCategory = apply(candidate.sourceCategory(), row.categorySource());
        boolean categoryInputsChanged = !candidate.race().id().equals(targetRace.id())
                || !Objects.equals(candidate.gender(), prospectiveGender)
                || !Objects.equals(candidate.birthDate(), prospectiveBirthDate)
                || !Objects.equals(candidate.sourceCategory(), prospectiveSourceCategory)
                || newCategory != null;
        if (categoryInputsChanged) {
            ImportPreviewDatabaseSnapshot.CategorySnapshot prospectiveCategory = AgeCategorySelection.resolve(
                    prospectiveBirthDate,
                    prospectiveGender,
                    prospectiveSourceCategory,
                    snapshot.eventStartsAt(),
                    snapshot.eventTimeZone(),
                    targetRace.ageCalculationMode(),
                    prospectiveCategories.stream()
                            .filter(category -> category.raceId().equals(targetRace.id()))
                            .map(category -> new AgeCategorySelection.Candidate<>(
                                    category,
                                    category.sourceName(),
                                    category.minAge(),
                                    category.maxAge(),
                                    category.gender(),
                                    category.enabled()
                            ))
                            .toList()
            );
            if (!sameCategory(candidate.category(), prospectiveCategory)) {
                diffs.add(diff(
                        "effectiveCategory",
                        categoryReference(candidate.category()),
                        categoryReference(prospectiveCategory)
                ));
            }
        }

        ImportPreviewDatabaseSnapshot.ResultSnapshot result = candidate.result();
        if (result == null) {
            diffs.add(diff("result", null, "CREATE"));
            return sorted(diffs);
        }
        if (!Objects.equals(result.status(), row.status())) {
            diffs.add(diff("status", result.status(), row.status()));
        }
        addSourceDiff(diffs, "gunTimeMs", result.gunTime(), row.gunTimeSource(), ImportPreviewPlanner::duration);
        addSourceDiff(diffs, "chipTimeMs", result.chipTime(), row.chipTimeSource(), ImportPreviewPlanner::duration);
        addSourceDiff(diffs, "overallPlace", result.overallPlace(), row.overallPlaceSource(), ImportPreviewPlanner::number);
        addSourceDiff(diffs, "genderPlace", result.genderPlace(), row.genderPlaceSource(), ImportPreviewPlanner::number);
        addSourceDiff(diffs, "categoryPlace", result.categoryPlace(), row.categoryPlaceSource(), ImportPreviewPlanner::number);
        addSourceDiff(diffs, "netOverallPlace", result.netOverallPlace(), row.netOverallPlaceSource(), ImportPreviewPlanner::number);
        addSourceDiff(diffs, "netGenderPlace", result.netGenderPlace(), row.netGenderPlaceSource(), ImportPreviewPlanner::number);
        addSourceDiff(diffs, "netCategoryPlace", result.netCategoryPlace(), row.netCategoryPlaceSource(), ImportPreviewPlanner::number);
        return sorted(diffs);
    }

    private static ClusterResolution resolveCluster(
            TimingResultImportRow row,
            ImportPreviewDatabaseSnapshot.RaceSnapshot targetRace,
            List<ImportPreviewDatabaseSnapshot.ClusterSnapshot> clusters
    ) {
        List<SourceField<String>> fields = List.of(
                row.clusterCodeSource(), row.clusterNameSource(), row.clusterSourceNameSource()
        );
        boolean anyColumn = fields.stream().anyMatch(field -> !field.isAbsent());
        boolean anyValue = fields.stream().anyMatch(SourceField::hasValue);
        if (!anyColumn) {
            return new ClusterResolution(SourceFieldState.ABSENT, null, null, null);
        }
        if (!anyValue) {
            return new ClusterResolution(SourceFieldState.EMPTY, null, null, null);
        }

        List<ImportPreviewDatabaseSnapshot.ClusterSnapshot> targetClusters = clusters.stream()
                .filter(cluster -> cluster.raceId().equals(targetRace.id()))
                .filter(cluster -> matches(row, cluster))
                .toList();
        if (targetClusters.size() == 1) {
            return new ClusterResolution(SourceFieldState.VALUE, targetClusters.getFirst(), null, null);
        }
        if (targetClusters.size() > 1) {
            return new ClusterResolution(SourceFieldState.VALUE, null,
                    "AMBIGUOUS_START_CLUSTER", "Source cluster matches more than one target race cluster");
        }

        boolean targetIdentifiersConflict = clusters.stream()
                .filter(cluster -> cluster.raceId().equals(targetRace.id()))
                .anyMatch(cluster -> matchesAny(row, cluster));
        if (targetIdentifiersConflict) {
            return new ClusterResolution(SourceFieldState.VALUE, null,
                    "START_CLUSTER_IDENTIFIERS_CONFLICT", "Source cluster identifiers refer to different clusters");
        }
        boolean crossRace = clusters.stream()
                .filter(cluster -> !cluster.raceId().equals(targetRace.id()))
                .anyMatch(cluster -> matches(row, cluster));
        if (crossRace) {
            return new ClusterResolution(SourceFieldState.VALUE, null,
                    "CROSS_RACE_START_CLUSTER", "Source cluster belongs to another race");
        }
        return new ClusterResolution(SourceFieldState.VALUE, null,
                "UNKNOWN_START_CLUSTER", "Source cluster is not configured in the target race");
    }

    private static boolean matches(TimingResultImportRow row, ImportPreviewDatabaseSnapshot.ClusterSnapshot cluster) {
        return matchesField(row.clusterCodeSource(), cluster.code())
                && matchesField(row.clusterNameSource(), cluster.displayName())
                && matchesField(row.clusterSourceNameSource(), cluster.sourceName());
    }

    private static boolean matchesAny(TimingResultImportRow row, ImportPreviewDatabaseSnapshot.ClusterSnapshot cluster) {
        return matchesOne(row.clusterCodeSource(), cluster.code())
                || matchesOne(row.clusterNameSource(), cluster.displayName())
                || matchesOne(row.clusterSourceNameSource(), cluster.sourceName());
    }

    private static boolean matchesField(SourceField<String> source, String value) {
        return !source.hasValue() || Objects.equals(source.value(), value);
    }

    private static boolean matchesOne(SourceField<String> source, String value) {
        return source.hasValue() && Objects.equals(source.value(), value);
    }

    private static DuplicateAnalysis duplicates(
            List<TimingResultImportRow> rows,
            Map<String, ImportPreviewDatabaseSnapshot.RaceSnapshot> races
    ) {
        Map<String, List<TimingResultImportRow>> byBib = new LinkedHashMap<>();
        for (TimingResultImportRow row : rows) {
            String bib = normalizeBib(row.bib());
            if (bib != null) {
                byBib.computeIfAbsent(bib, ignored -> new ArrayList<>()).add(row);
            }
        }
        List<ImportPreviewResponseDto.DuplicateBib> diagnostics = byBib.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ImportPreviewResponseDto.DuplicateBib(
                        entry.getKey(),
                        entry.getValue().size(),
                        false,
                        entry.getValue().stream()
                                .sorted(Comparator.comparingInt(TimingResultImportRow::sourceRowNumber))
                                .map(row -> new ImportPreviewResponseDto.DuplicateBibRow(
                                        row.sourceRowNumber(),
                                        ImportDisplayName.from(row),
                                        row.birthDate() == null ? null : row.birthDate().toString(),
                                        raceRef(races.get(row.raceCode()))
                                ))
                                .toList()
                ))
                .toList();
        Set<String> duplicateBibs = diagnostics.stream()
                .map(ImportPreviewResponseDto.DuplicateBib::bib)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new DuplicateAnalysis(duplicateBibs, diagnostics);
    }

    private static DiagnosticAnalysis diagnostics(
            TimingCsvParseResult parsed,
            List<ImportErrorDto> validationErrors
    ) {
        Map<Integer, List<ImportPreviewResponseDto.Diagnostic>> byRow = new HashMap<>();
        List<ImportPreviewResponseDto.Diagnostic> all = new ArrayList<>();
        for (TimingCsvRowError error : parsed.errors()) {
            ImportPreviewResponseDto.Diagnostic diagnostic = new ImportPreviewResponseDto.Diagnostic(
                    error.sourceRowNumber(), error.column(), "CSV_VALUE_INVALID", error.message()
            );
            add(byRow, error.sourceRowNumber(), diagnostic);
            all.add(diagnostic);
        }
        for (ImportErrorDto error : validationErrors) {
            ImportPreviewResponseDto.Diagnostic diagnostic = new ImportPreviewResponseDto.Diagnostic(
                    error.row(), error.field(), "SOURCE_VALUE_INVALID", error.message()
            );
            if (error.row() != null) {
                add(byRow, error.row(), diagnostic);
            }
            all.add(diagnostic);
        }
        return new DiagnosticAnalysis(byRow, all);
    }

    private static void add(
            Map<Integer, List<ImportPreviewResponseDto.Diagnostic>> diagnostics,
            int row,
            ImportPreviewResponseDto.Diagnostic diagnostic
    ) {
        diagnostics.computeIfAbsent(row, ignored -> new ArrayList<>()).add(diagnostic);
    }

    private static ImportPreviewResponseDto.Totals totals(
            int totalRows,
            List<ImportPreviewResponseDto.Row> rows
    ) {
        return new ImportPreviewResponseDto.Totals(
                totalRows,
                (int) rows.stream().filter(row -> row.decision() != ImportPreviewDecision.OUT_OF_SCOPE).count(),
                count(rows, ImportPreviewDecision.NEW),
                count(rows, ImportPreviewDecision.EXISTING_UNCHANGED),
                count(rows, ImportPreviewDecision.EXISTING_CHANGED),
                count(rows, ImportPreviewDecision.AMBIGUOUS),
                count(rows, ImportPreviewDecision.CONFLICT),
                count(rows, ImportPreviewDecision.INVALID),
                count(rows, ImportPreviewDecision.DUPLICATE_IN_FILE),
                count(rows, ImportPreviewDecision.OUT_OF_SCOPE)
        );
    }

    private static ImportPreviewResponseDto.ModeSummary modeSummary(
            ImportOperationMode mode,
            ImportPreviewResponseDto.Totals totals
    ) {
        int blocked = totals.ambiguousCount() + totals.conflictCount()
                + totals.invalidCount() + totals.duplicateCount();
        if (mode == ImportOperationMode.EMERGENCY_REPLACE) {
            return new ImportPreviewResponseDto.ModeSummary(
                    totals.newCount(), 0, 0, 0, 0, blocked
            );
        }
        if (mode == ImportOperationMode.ADD_NEW) {
            return new ImportPreviewResponseDto.ModeSummary(
                    totals.newCount(), 0, totals.unchangedCount() + totals.changedCount(),
                    0, totals.unchangedCount(), blocked
            );
        }
        return new ImportPreviewResponseDto.ModeSummary(
                0, totals.changedCount(), 0, totals.newCount(), totals.unchangedCount(), blocked
        );
    }

    private static int count(List<ImportPreviewResponseDto.Row> rows, ImportPreviewDecision decision) {
        return (int) rows.stream().filter(row -> row.decision() == decision).count();
    }

    private static ImportPreviewResponseDto.Row row(
            TimingResultImportRow source,
            String bib,
            String participantName,
            ImportPreviewResponseDto.RaceRef targetRace,
            ImportPreviewDatabaseSnapshot.RegistrationSnapshot candidate,
            ImportPreviewDecision decision,
            ImportPreviewAction action,
            String reasonCode,
            String reason,
            List<ImportPreviewResponseDto.FieldDiff> diffs
    ) {
        return new ImportPreviewResponseDto.Row(
                source.sourceRowNumber(), bib, participantName, targetRace,
                candidate == null ? null : candidate.id(),
                candidate == null || candidate.result() == null ? null : candidate.result().id(),
                decision, action, reasonCode, reason, sorted(diffs)
        );
    }

    private static ImportPreviewAction action(
            ImportOperationMode mode,
            ImportPreviewDecision decision,
            ImportPreviewDatabaseSnapshot.RegistrationSnapshot candidate
    ) {
        return switch (decision) {
            case NEW -> mode == ImportOperationMode.ADD_NEW ? ImportPreviewAction.INSERT : ImportPreviewAction.SKIP;
            case EXISTING_CHANGED -> mode == ImportOperationMode.UPDATE_EXISTING
                    ? candidate != null && candidate.result() == null
                        ? ImportPreviewAction.CREATE_RESULT
                        : ImportPreviewAction.UPDATE
                    : ImportPreviewAction.SKIP;
            case EXISTING_UNCHANGED, RETIRED, OUT_OF_SCOPE -> ImportPreviewAction.SKIP;
            case AMBIGUOUS, CONFLICT, INVALID, DUPLICATE_IN_FILE -> ImportPreviewAction.BLOCKED;
        };
    }

    private static ImportPreviewResponseDto.RaceRef raceRef(
            ImportPreviewDatabaseSnapshot.RaceSnapshot race
    ) {
        return race == null ? null : new ImportPreviewResponseDto.RaceRef(race.id(), race.name());
    }

    private static String displayRace(ImportPreviewDatabaseSnapshot.RaceSnapshot race) {
        return race.id() + ":" + race.name();
    }

    private static List<ImportPreviewResponseDto.FieldDiff> raceDiff(
            ImportPreviewDatabaseSnapshot.RaceSnapshot current,
            ImportPreviewDatabaseSnapshot.RaceSnapshot target
    ) {
        return List.of(diff("race", displayRace(current), displayRace(target)));
    }

    private static String displayCluster(ImportPreviewDatabaseSnapshot.ClusterSnapshot cluster) {
        return cluster == null ? null : cluster.id() + ":" + cluster.displayName();
    }

    private static List<ImportPreviewResponseDto.FieldDiff> categoryDefinitionDiffs(
            TimingResultImportRow row,
            ImportPreviewDatabaseSnapshot.RaceSnapshot targetRace,
            List<ImportPreviewDatabaseSnapshot.CategorySnapshot> categories
    ) {
        ImportPreviewDatabaseSnapshot.CategorySnapshot category = missingSourceCategory(row, targetRace, categories);
        return category == null
                ? List.of()
                : List.of(diff("categoryDefinition", null, categoryReference(category)));
    }

    private static CategoryBlock categoryBlock(
            LocalDate birthDate,
            String gender,
            String sourceCategory,
            TimingResultImportRow row,
            ImportPreviewDatabaseSnapshot.RaceSnapshot targetRace,
            ImportPreviewDatabaseSnapshot snapshot
    ) {
        List<ImportPreviewDatabaseSnapshot.CategorySnapshot> categories = new ArrayList<>(snapshot.categories());
        ImportPreviewDatabaseSnapshot.CategorySnapshot missing = missingSourceCategory(
                row, targetRace, snapshot.categories()
        );
        if (missing != null && Objects.equals(sourceCategory, row.category())) {
            categories.add(missing);
        }
        AgeCategoryResolution<ImportPreviewDatabaseSnapshot.CategorySnapshot> resolution =
                AgeCategorySelection.resolveDetailed(
                        birthDate,
                        gender,
                        sourceCategory,
                        snapshot.eventStartsAt(),
                        snapshot.eventTimeZone(),
                        targetRace.ageCalculationMode(),
                        categories.stream()
                                .filter(category -> category.raceId().equals(targetRace.id()))
                                .map(category -> new AgeCategorySelection.Candidate<>(
                                        category,
                                        category.sourceName(),
                                        category.minAge(),
                                        category.maxAge(),
                                        category.gender(),
                                        category.enabled()
                                ))
                                .toList()
                );
        return resolution.blocked()
                ? new CategoryBlock(resolution.blockingCode(), resolution.blockingMessage())
                : null;
    }

    private static ImportPreviewDatabaseSnapshot.CategorySnapshot missingSourceCategory(
            TimingResultImportRow row,
            ImportPreviewDatabaseSnapshot.RaceSnapshot targetRace,
            List<ImportPreviewDatabaseSnapshot.CategorySnapshot> categories
    ) {
        if (!row.categorySource().hasValue()) return null;
        boolean exists = categories.stream().anyMatch(category -> category.raceId().equals(targetRace.id())
                && category.sourceName().equals(row.categorySource().value()));
        if (exists) return null;
        int displayOrder = categories.stream()
                .filter(category -> category.raceId().equals(targetRace.id()))
                .mapToInt(ImportPreviewDatabaseSnapshot.CategorySnapshot::displayOrder)
                .max()
                .orElse(-1) + 1;
        return new ImportPreviewDatabaseSnapshot.CategorySnapshot(
                null,
                targetRace.id(),
                row.categorySource().value(),
                row.categorySource().value(),
                displayOrder,
                null,
                null,
                null,
                true
        );
    }

    private static boolean sameCategory(
            ImportPreviewDatabaseSnapshot.CategorySnapshot left,
            ImportPreviewDatabaseSnapshot.CategorySnapshot right
    ) {
        return Objects.equals(categoryKey(left), categoryKey(right));
    }

    private static String categoryKey(ImportPreviewDatabaseSnapshot.CategorySnapshot category) {
        if (category == null) return null;
        return category.id() == null
                ? "NEW:" + category.raceId() + ":" + category.sourceName()
                : "ID:" + category.id();
    }

    private static String categoryReference(ImportPreviewDatabaseSnapshot.CategorySnapshot category) {
        if (category == null) return null;
        return category.id() == null
                ? "CREATE:" + category.sourceName()
                : category.id() + ":" + category.displayName();
    }

    private static String displayName(String firstName, String lastName, String bib) {
        if (firstName != null && lastName != null) return firstName + " " + lastName;
        if (firstName != null) return firstName;
        if (lastName != null) return lastName;
        return bib == null ? null : "Bib " + bib;
    }

    private static String normalizeBib(String bib) {
        if (bib == null) return null;
        String value = bib.strip();
        return value.isEmpty() ? null : value;
    }

    private static <T> T apply(T current, SourceField<T> source) {
        return source.isAbsent() ? current : source.valueOrNull();
    }

    private static <T> void addSourceDiff(
            List<ImportPreviewResponseDto.FieldDiff> diffs,
            String field,
            T oldValue,
            SourceField<T> source,
            Function<T, String> formatter
    ) {
        if (source.isAbsent()) return;
        T newValue = source.valueOrNull();
        if (!Objects.equals(oldValue, newValue)) {
            diffs.add(diff(field, formatter.apply(oldValue), formatter.apply(newValue)));
        }
    }

    private static ImportPreviewResponseDto.FieldDiff diff(String field, String oldValue, String newValue) {
        return new ImportPreviewResponseDto.FieldDiff(field, oldValue, newValue);
    }

    private static List<ImportPreviewResponseDto.FieldDiff> sorted(
            List<ImportPreviewResponseDto.FieldDiff> diffs
    ) {
        return diffs.stream().sorted(Comparator.comparing(ImportPreviewResponseDto.FieldDiff::field)).toList();
    }

    private static String text(String value) { return value; }
    private static String date(LocalDate value) { return value == null ? null : value.toString(); }
    private static String duration(Duration value) { return value == null ? null : Long.toString(value.toMillis()); }
    private static String number(Number value) { return value == null ? null : value.toString(); }

    private static Comparator<ImportPreviewResponseDto.Diagnostic> diagnosticOrder() {
        return Comparator.comparing(
                        ImportPreviewResponseDto.Diagnostic::sourceRowNumber,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                )
                .thenComparing(ImportPreviewResponseDto.Diagnostic::field, Comparator.nullsFirst(String::compareTo))
                .thenComparing(ImportPreviewResponseDto.Diagnostic::code);
    }

    private static String digest(
            Long eventId,
            ImportOperationMode mode,
            List<Long> scopeRaceIds,
            String fileSha256,
            long baseRevision,
            List<ImportPreviewResponseDto.Row> rows,
            List<ImportPreviewResponseDto.DuplicateBib> duplicateBibs,
            List<ImportPreviewResponseDto.Diagnostic> diagnostics,
            ImportPreviewDatabaseSnapshot snapshot,
            ImportPreviewResponseDto.EmergencySummary emergencySummary
    ) {
        DigestWriter writer = new DigestWriter();
        writer.add(eventId).add(mode).add(fileSha256).add(baseRevision);
        scopeRaceIds.stream().sorted().forEach(writer::add);
        rows.stream().sorted(Comparator.comparingInt(ImportPreviewResponseDto.Row::sourceRowNumber)).forEach(row -> {
            writer.add(row.sourceRowNumber()).add(row.bib()).add(row.decision()).add(row.futureAction())
                    .add(row.reasonCode()).add(row.matchedRegistrationId()).add(row.matchedResultId());
            if (row.targetRace() != null) {
                writer.add(row.targetRace().raceId());
            }
            row.diffs().stream().sorted(Comparator.comparing(ImportPreviewResponseDto.FieldDiff::field))
                    .forEach(diff -> writer.add(diff.field()).add(diff.oldValue()).add(diff.newValue()));
        });
        duplicateBibs.stream().sorted(Comparator.comparing(ImportPreviewResponseDto.DuplicateBib::bib))
                .forEach(duplicate -> {
                    writer.add(duplicate.bib()).add(duplicate.count());
                    duplicate.rows().stream().sorted(Comparator.comparingInt(
                                    ImportPreviewResponseDto.DuplicateBibRow::sourceRowNumber
                            ))
                            .forEach(row -> writer.add(row.sourceRowNumber()).add(row.birthDate()));
                });
        diagnostics.stream().sorted(diagnosticOrder())
                .forEach(diagnostic -> writer.add(diagnostic.sourceRowNumber()).add(diagnostic.field())
                        .add(diagnostic.code()).add(diagnostic.message()));
        if (emergencySummary != null) {
            Set<Long> scope = new LinkedHashSet<>(scopeRaceIds);
            snapshot.races().stream()
                    .filter(race -> scope.contains(race.id()))
                    .sorted(Comparator.comparing(ImportPreviewDatabaseSnapshot.RaceSnapshot::id))
                    .forEach(race -> writer.add("RACE").add(race.id()).add(race.resultsPublicationStatus()));
            snapshot.registrations().stream()
                    .filter(registration -> scope.contains(registration.race().id()))
                    .sorted(Comparator.comparing(ImportPreviewDatabaseSnapshot.RegistrationSnapshot::id))
                    .forEach(registration -> writer.add("RETIRE").add(registration.id())
                            .add(registration.race().id()));
            snapshot.activeIssues().stream()
                    .filter(issue -> scope.contains(issue.raceId()))
                    .sorted(Comparator.comparing(ImportPreviewDatabaseSnapshot.ActiveIssueSnapshot::id))
                    .forEach(issue -> writer.add("ARCHIVE").add(issue.id()).add(issue.registrationId())
                            .add(issue.raceId()));
        }
        return writer.finish();
    }

    private static ImportPreviewResponseDto.EmergencySummary emergencySummary(
            String sourceFilename,
            String fileSha256,
            List<Long> scopeRaceIds,
            ImportPreviewDatabaseSnapshot snapshot,
            List<ImportPreviewResponseDto.Row> rows,
            ImportPreviewResponseDto.Totals totals,
            List<ImportPreviewResponseDto.Diagnostic> diagnostics
    ) {
        List<ImportPreviewResponseDto.EmergencyRaceSummary> raceSummaries = scopeRaceIds.stream()
                .sorted()
                .map(raceId -> {
                    ImportPreviewDatabaseSnapshot.RaceSnapshot race = snapshot.races().stream()
                            .filter(candidate -> candidate.id().equals(raceId))
                            .findFirst()
                            .orElseThrow();
                    int current = (int) snapshot.registrations().stream()
                            .filter(registration -> registration.race().id().equals(raceId)).count();
                    int source = (int) rows.stream()
                            .filter(row -> row.targetRace() != null && row.targetRace().raceId().equals(raceId))
                            .filter(row -> row.decision() == ImportPreviewDecision.NEW).count();
                    int issues = (int) snapshot.activeIssues().stream()
                            .filter(issue -> issue.raceId().equals(raceId)).count();
                    return new ImportPreviewResponseDto.EmergencyRaceSummary(
                            raceRef(race), race.resultsPublicationStatus().name(),
                            current, source, current, source, issues
                    );
                })
                .toList();
        int current = raceSummaries.stream().mapToInt(ImportPreviewResponseDto.EmergencyRaceSummary::currentCount).sum();
        int source = raceSummaries.stream().mapToInt(ImportPreviewResponseDto.EmergencyRaceSummary::sourceCount).sum();
        int issues = raceSummaries.stream()
                .mapToInt(ImportPreviewResponseDto.EmergencyRaceSummary::activeIssuesWouldArchiveCount).sum();
        int blocking = totals.ambiguousCount() + totals.conflictCount() + totals.invalidCount()
                + totals.duplicateCount() + (int) diagnostics.stream()
                .filter(diagnostic -> diagnostic.sourceRowNumber() == null).count();
        return new ImportPreviewResponseDto.EmergencySummary(
                true,
                new ImportPreviewResponseDto.EventRef(
                        snapshot.eventName(), snapshot.eventLocation(), snapshot.eventStartsAt()
                ),
                new ImportPreviewResponseDto.FileRef(sourceFilename, fileSha256),
                raceSummaries,
                new ImportPreviewResponseDto.EmergencyTotals(
                        current, source, current, source, issues, totals.outOfScopeCount(), blocking
                )
        );
    }

    private record DuplicateAnalysis(
            Set<String> duplicateBibs,
            List<ImportPreviewResponseDto.DuplicateBib> diagnostics
    ) {
    }

    private record DiagnosticAnalysis(
            Map<Integer, List<ImportPreviewResponseDto.Diagnostic>> byRow,
            List<ImportPreviewResponseDto.Diagnostic> all
    ) {
    }

    private record ClusterResolution(
            SourceFieldState state,
            ImportPreviewDatabaseSnapshot.ClusterSnapshot cluster,
            String errorCode,
            String errorMessage
    ) {
    }

    private record CategoryBlock(String code, String message) {
    }

    private static final class DigestWriter {
        private final MessageDigest digest;

        private DigestWriter() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is not available", exception);
            }
        }

        private DigestWriter add(Object value) {
            byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
            digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) ':');
            digest.update(bytes);
            digest.update((byte) '\n');
            return this;
        }

        private String finish() {
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
