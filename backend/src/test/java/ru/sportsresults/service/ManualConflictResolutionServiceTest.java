package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import ru.sportsresults.api.dto.ImportPreviewResponseDto;
import ru.sportsresults.api.dto.ManualConflictResolutionDto;
import ru.sportsresults.domain.ImportOperationMode;
import ru.sportsresults.importing.ManualConflictDecision;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualConflictResolutionServiceTest {

    private final ManualConflictResolutionService service = new ManualConflictResolutionService();

    @Test
    void unresolvedConflictIsRejected() {
        assertThatThrownBy(() -> service.resolve(
                ImportOperationMode.UPDATE_EXISTING,
                plan(conflict(2, "gunTimeMs")),
                List.of()
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("MANUAL_CONFLICT_RESOLUTION_REQUIRED");
    }

    @Test
    void supportsKeepAcceptBulkAndMixedParticipants() {
        ImportPreviewPlan plan = plan(
                conflict(2, "gunTimeMs"),
                conflict(2, "chipTimeMs"),
                conflict(3, "status")
        );
        ManualConflictResolutionPlan mixed = service.resolve(
                ImportOperationMode.UPDATE_EXISTING,
                plan,
                List.of(
                        resolution(2, "gunTimeMs", ManualConflictDecision.KEEP_MANUAL),
                        resolution(2, "chipTimeMs", ManualConflictDecision.ACCEPT_SOURCE),
                        resolution(3, "status", ManualConflictDecision.KEEP_MANUAL)
                )
        );
        assertThat(mixed.keepManualFields(2)).containsExactly("gunTimeMs");
        assertThat(mixed.acceptSourceFields(2)).containsExactly("chipTimeMs");
        assertThat(mixed.keepManualFields(3)).containsExactly("status");
        assertThat(mixed.resolvedRows()).containsExactlyInAnyOrder(2, 3);

        ManualConflictResolutionPlan keepAll = service.resolve(
                ImportOperationMode.UPDATE_EXISTING,
                plan,
                List.of(
                        resolution(2, "gunTimeMs", ManualConflictDecision.KEEP_MANUAL),
                        resolution(2, "chipTimeMs", ManualConflictDecision.KEEP_MANUAL),
                        resolution(3, "status", ManualConflictDecision.KEEP_MANUAL)
                )
        );
        assertThat(keepAll.keepManualFields(2)).containsExactlyInAnyOrder("gunTimeMs", "chipTimeMs");
        assertThat(keepAll.keepManualFields(3)).containsExactly("status");

        ManualConflictResolutionPlan acceptAll = service.resolve(
                ImportOperationMode.UPDATE_EXISTING,
                plan,
                List.of(
                        resolution(2, "gunTimeMs", ManualConflictDecision.ACCEPT_SOURCE),
                        resolution(2, "chipTimeMs", ManualConflictDecision.ACCEPT_SOURCE),
                        resolution(3, "status", ManualConflictDecision.ACCEPT_SOURCE)
                )
        );
        assertThat(acceptAll.acceptSourceFields(2)).containsExactlyInAnyOrder("gunTimeMs", "chipTimeMs");
        assertThat(acceptAll.acceptSourceFields(3)).containsExactly("status");
    }

    @Test
    void rejectsForgedUnsupportedAndDuplicateResolutions() {
        ImportPreviewPlan plan = plan(conflict(2, "gunTimeMs"));
        assertInvalid(plan, List.of(resolution(3, "gunTimeMs", ManualConflictDecision.KEEP_MANUAL)),
                "INVALID_MANUAL_CONFLICT_RESOLUTION");
        assertInvalid(plan, List.of(resolution(2, "chipTimeMs", ManualConflictDecision.KEEP_MANUAL)),
                "INVALID_MANUAL_CONFLICT_RESOLUTION");
        assertInvalid(plan, List.of(resolution(2, "displayName", ManualConflictDecision.KEEP_MANUAL)),
                "INVALID_MANUAL_CONFLICT_RESOLUTION");
        assertInvalid(plan, List.of(
                resolution(2, "gunTimeMs", ManualConflictDecision.KEEP_MANUAL),
                resolution(2, "gunTimeMs", ManualConflictDecision.ACCEPT_SOURCE)
        ), "DUPLICATE_MANUAL_CONFLICT_RESOLUTION");
    }

    @Test
    void rejectsResolutionsOutsideUpdateExisting() {
        assertThatThrownBy(() -> service.resolve(
                ImportOperationMode.EMERGENCY_REPLACE,
                plan(),
                List.of(resolution(2, "gunTimeMs", ManualConflictDecision.ACCEPT_SOURCE))
        )).isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getCode())
                .isEqualTo("MANUAL_CONFLICT_RESOLUTIONS_NOT_ALLOWED");
    }

    private void assertInvalid(
            ImportPreviewPlan plan,
            List<ManualConflictResolutionDto> resolutions,
            String code
    ) {
        assertThatThrownBy(() -> service.resolve(ImportOperationMode.UPDATE_EXISTING, plan, resolutions))
                .isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getCode())
                .isEqualTo(code);
    }

    private static ManualConflictResolutionDto resolution(
            int row,
            String field,
            ManualConflictDecision decision
    ) {
        return new ManualConflictResolutionDto(row, field, decision);
    }

    private static ImportPreviewResponseDto.ManualConflict conflict(int row, String field) {
        return new ImportPreviewResponseDto.ManualConflict(
                row, "B-" + row, "Runner " + row, (long) row, field, "old", "new"
        );
    }

    private static ImportPreviewPlan plan(ImportPreviewResponseDto.ManualConflict... conflicts) {
        boolean blocking = conflicts.length > 0;
        return new ImportPreviewPlan(
                new ImportPreviewResponseDto.Totals(0, 0, 0, 0, 0, 0, conflicts.length, 0, 0, 0),
                new ImportPreviewResponseDto.ModeSummary(0, conflicts.length, 0, 0, 0, 0),
                blocking,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(conflicts),
                "digest",
                null
        );
    }
}
