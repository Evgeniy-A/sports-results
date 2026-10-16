package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.Result;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ManualResultCorrectionFieldsTest {

    @Test
    void keepsOnlyFieldsWhoseLatestAuditedValueStillMatchesCurrentResult() {
        Result result = new Result();
        result.setStatus("finished");
        result.setGunTime(Duration.ofMillis(1_500));
        result.setChipTime(Duration.ofMillis(1_400));

        AdminChangeLog oldGun = change("gunTimeMs", "1000");
        AdminChangeLog currentGun = change("gunTimeMs", "1500");
        AdminChangeLog staleChip = change("chipTimeMs", "1300");
        AdminChangeLog status = change("status", "finished");
        AdminChangeLog unrelated = change("displayName", "Runner");

        assertThat(ManualResultCorrectionFields.active(
                result, List.of(oldGun, currentGun, staleChip, status, unrelated)
        )).containsExactlyInAnyOrder("gunTimeMs", "status");
    }

    @Test
    void recognizesAuditedClearAsAnActiveManualCorrection() {
        Result result = new Result();
        result.setStatus("notstarted");
        result.setGunTime(null);

        assertThat(ManualResultCorrectionFields.active(
                result, List.of(change("gunTimeMs", null))
        )).containsExactly("gunTimeMs");
    }

    @Test
    void acceptingSourceDeactivatesOldManualOverrideWithoutBecomingANewOne() {
        Result result = new Result();
        result.setStatus("finished");
        result.setGunTime(Duration.ofMillis(2_000));

        AdminChangeLog manual = change("gunTimeMs", "1500");
        AdminChangeLog accepted = change(
                ManualResultCorrectionFields.acceptedSourceAuditField(UUID.randomUUID(), "gunTimeMs"),
                "2000"
        );
        assertThat(ManualResultCorrectionFields.active(result, List.of(manual, accepted))).isEmpty();

        result.setGunTime(Duration.ofMillis(1_500));
        assertThat(ManualResultCorrectionFields.active(result, List.of(manual, accepted))).isEmpty();
    }

    private static AdminChangeLog change(String field, String newValue) {
        AdminChangeLog change = new AdminChangeLog();
        change.setFieldName(field);
        change.setNewValue(newValue);
        return change;
    }
}
