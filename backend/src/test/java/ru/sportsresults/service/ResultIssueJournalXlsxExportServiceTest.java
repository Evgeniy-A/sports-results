package ru.sportsresults.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResultIssueJournalXlsxExportServiceTest {

    @Test
    void neutralizesFormulaPrefixesAndBoundsTextAtExcelsCellLimit() {
        for (char prefix : new char[]{'=', '+', '-', '@', '\t', '\r'}) {
            assertThat(ResultIssueJournalXlsxExportService.safeText(prefix + "опасное значение"))
                    .startsWith("'");
        }
        String bounded = ResultIssueJournalXlsxExportService.safeText("=" + "Я".repeat(40_000));
        assertThat(bounded)
                .hasSize(32_767)
                .startsWith("'=")
                .endsWith(" … [TRUNCATED]");
        assertThat(ResultIssueJournalXlsxExportService.safeText("обычный текст"))
                .isEqualTo("обычный текст");
    }
}
