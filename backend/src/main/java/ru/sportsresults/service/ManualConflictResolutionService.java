package ru.sportsresults.service;

import org.springframework.stereotype.Component;
import ru.sportsresults.api.dto.ImportPreviewResponseDto;
import ru.sportsresults.api.dto.ManualConflictResolutionDto;
import ru.sportsresults.domain.ImportOperationMode;
import ru.sportsresults.importing.ManualConflictDecision;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
class ManualConflictResolutionService {

    ManualConflictResolutionPlan resolve(
            ImportOperationMode mode,
            ImportPreviewPlan plan,
            List<ManualConflictResolutionDto> requested
    ) {
        List<ManualConflictResolutionDto> safeRequested = requested == null ? List.of() : requested;
        if (mode != ImportOperationMode.UPDATE_EXISTING) {
            if (!safeRequested.isEmpty()) {
                throw new InvalidRequestException(
                        "MANUAL_CONFLICT_RESOLUTIONS_NOT_ALLOWED",
                        "Manual conflict resolutions are allowed only for UPDATE_EXISTING"
                );
            }
            return ManualConflictResolutionPlan.empty();
        }

        Map<ConflictKey, ImportPreviewResponseDto.ManualConflict> expected = new LinkedHashMap<>();
        for (ImportPreviewResponseDto.ManualConflict conflict : plan.manualConflicts()) {
            expected.put(new ConflictKey(conflict.sourceRowNumber(), conflict.field()), conflict);
        }

        Map<ConflictKey, ManualConflictDecision> decisions = new LinkedHashMap<>();
        for (ManualConflictResolutionDto resolution : safeRequested) {
            validateShape(resolution);
            ConflictKey key = new ConflictKey(resolution.sourceRowNumber(), resolution.field().strip());
            if (!ManualResultCorrectionFields.SUPPORTED_FIELDS.contains(key.field())) {
                throw new InvalidRequestException(
                        "INVALID_MANUAL_CONFLICT_RESOLUTION",
                        "Unsupported manual conflict field: " + key.field()
                );
            }
            if (!expected.containsKey(key)) {
                throw new InvalidRequestException(
                        "INVALID_MANUAL_CONFLICT_RESOLUTION",
                        "Resolution does not reference a current manual conflict"
                );
            }
            if (decisions.putIfAbsent(key, resolution.decision()) != null) {
                throw new InvalidRequestException(
                        "DUPLICATE_MANUAL_CONFLICT_RESOLUTION",
                        "A manual conflict may be resolved only once"
                );
            }
        }

        if (decisions.size() != expected.size()) {
            throw new RequestConflictException(
                    "MANUAL_CONFLICT_RESOLUTION_REQUIRED",
                    "Every manual result conflict requires an explicit decision"
            );
        }

        Map<Integer, Set<String>> keepByRow = new LinkedHashMap<>();
        Map<Integer, Set<String>> acceptByRow = new LinkedHashMap<>();
        decisions.forEach((key, decision) -> target(decision, keepByRow, acceptByRow)
                .computeIfAbsent(key.sourceRowNumber(), ignored -> new LinkedHashSet<>())
                .add(key.field()));
        return new ManualConflictResolutionPlan(keepByRow, acceptByRow);
    }

    private static Map<Integer, Set<String>> target(
            ManualConflictDecision decision,
            Map<Integer, Set<String>> keepByRow,
            Map<Integer, Set<String>> acceptByRow
    ) {
        return decision == ManualConflictDecision.KEEP_MANUAL ? keepByRow : acceptByRow;
    }

    private static void validateShape(ManualConflictResolutionDto resolution) {
        if (resolution == null
                || resolution.sourceRowNumber() == null
                || resolution.sourceRowNumber() <= 0
                || resolution.field() == null
                || resolution.field().isBlank()
                || resolution.decision() == null) {
            throw new InvalidRequestException(
                    "INVALID_MANUAL_CONFLICT_RESOLUTION",
                    "sourceRowNumber, field and decision are required"
            );
        }
    }

    private record ConflictKey(int sourceRowNumber, String field) {
    }
}

record ManualConflictResolutionPlan(
        Map<Integer, Set<String>> keepManualByRow,
        Map<Integer, Set<String>> acceptSourceByRow
) {
    ManualConflictResolutionPlan {
        keepManualByRow = immutable(keepManualByRow);
        acceptSourceByRow = immutable(acceptSourceByRow);
    }

    static ManualConflictResolutionPlan empty() {
        return new ManualConflictResolutionPlan(Map.of(), Map.of());
    }

    Set<String> keepManualFields(int sourceRowNumber) {
        return keepManualByRow.getOrDefault(sourceRowNumber, Set.of());
    }

    Set<String> acceptSourceFields(int sourceRowNumber) {
        return acceptSourceByRow.getOrDefault(sourceRowNumber, Set.of());
    }

    Set<Integer> resolvedRows() {
        Set<Integer> rows = new LinkedHashSet<>(keepManualByRow.keySet());
        rows.addAll(acceptSourceByRow.keySet());
        return Set.copyOf(rows);
    }

    private static Map<Integer, Set<String>> immutable(Map<Integer, Set<String>> source) {
        Map<Integer, Set<String>> copy = new LinkedHashMap<>();
        source.forEach((row, fields) -> copy.put(row, Set.copyOf(fields)));
        return Map.copyOf(copy);
    }
}
