package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.RegistrationEntryKind;
import ru.sportsresults.importing.SourceField;
import ru.sportsresults.importing.TimingResultImportRow;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ImportValidatorTest {

    private final ImportValidator validator = new ImportValidator();

    @Test
    void acceptsEveryCanonicalStatus() {
        for (String status : List.of("finished", "running", "notstarted", "disqualified", "quarantine")) {
            assertThat(validator.validate(List.of(row(status))))
                    .as("status %s", status)
                    .isEmpty();
        }
    }

    @Test
    void rejectsBlankUnknownAndOverlengthStatus() {
        assertThat(validator.validate(List.of(row(" "))))
                .anySatisfy(error -> {
                    assertThat(error.field()).isEqualTo("status");
                    assertThat(error.message()).contains("required");
                });
        assertThat(validator.validate(List.of(row("UNKNOWN_RESULT"))))
                .anySatisfy(error -> {
                    assertThat(error.field()).isEqualTo("status");
                    assertThat(error.message()).contains("Unknown result status");
                });
        assertThat(validator.validate(List.of(row("x".repeat(65)))))
                .anySatisfy(error -> {
                    assertThat(error.field()).isEqualTo("status");
                    assertThat(error.message()).contains("exceeds 64");
                });
    }

    private static TimingResultImportRow row(String status) {
        return new TimingResultImportRow(
                2,
                "hash",
                SourceField.value("Test"),
                SourceField.value("Runner"),
                SourceField.value("male"),
                SourceField.absent(),
                "Race A",
                "A-1",
                SourceField.absent(),
                SourceField.absent(),
                SourceField.absent(),
                SourceField.absent(),
                status,
                RegistrationEntryKind.PERSON,
                SourceField.value(Duration.ofHours(1)),
                SourceField.absent(),
                SourceField.absent(),
                SourceField.absent(),
                SourceField.absent(),
                SourceField.absent(),
                SourceField.absent(),
                SourceField.absent()
        );
    }
}
