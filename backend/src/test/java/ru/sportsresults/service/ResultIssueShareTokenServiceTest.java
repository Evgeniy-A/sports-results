package ru.sportsresults.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ResultIssueShareTokenServiceTest {

    @Test
    void generatesUniqueUrlSafeThirtyTwoByteSecretsAndStoresOnlySha256Shape() {
        ResultIssueShareTokenService service = new ResultIssueShareTokenService();
        Set<String> rawTokens = new HashSet<>();
        Set<String> hashes = new HashSet<>();

        for (int index = 0; index < 1_000; index++) {
            GeneratedShareToken generated = service.generate();
            assertThat(generated.rawToken())
                    .hasSize(43)
                    .matches("[A-Za-z0-9_-]+");
            assertThat(generated.tokenHash())
                    .hasSize(64)
                    .matches("[0-9a-f]+");
            assertThat(generated.tokenHash()).isEqualTo(service.hash(generated.rawToken()));
            assertThat(generated.tokenHash()).isNotEqualTo(generated.rawToken());
            rawTokens.add(generated.rawToken());
            hashes.add(generated.tokenHash());
        }

        assertThat(rawTokens).hasSize(1_000);
        assertThat(hashes).hasSize(1_000);
    }
}
