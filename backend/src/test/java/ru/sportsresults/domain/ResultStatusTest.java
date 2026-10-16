package ru.sportsresults.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultStatusTest {

    @Test
    void acceptsEveryCanonicalStatusAndNormalizesCase() {
        assertThat(ResultStatus.normalize("finished")).contains("finished");
        assertThat(ResultStatus.normalize("RUNNING")).contains("running");
        assertThat(ResultStatus.normalize(" NotStarted ")).contains("notstarted");
        assertThat(ResultStatus.normalize("disqualified")).contains("disqualified");
        assertThat(ResultStatus.normalize("quarantine")).contains("quarantine");
    }

    @Test
    void rejectsUnknownStatus() {
        assertThat(ResultStatus.normalize("UNKNOWN_RESULT")).isEmpty();
    }
}
