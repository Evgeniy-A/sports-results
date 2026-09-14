package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV23UpgradeTest {

    private static final List<String> JOURNAL_INDEXES = List.of(
            "ix_result_issue_journal_created",
            "ix_result_issue_journal_event_created",
            "ix_result_issue_journal_bib_created",
            "ix_result_issue_journal_race_created",
            "ix_result_issue_journal_event_date_created"
    );

    @Test
    void upgradesV22AndAddsOnlyJournalIndexes() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("22").load().migrate();

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("23").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("23");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForList("""
                    SELECT indexname
                    FROM pg_indexes
                    WHERE schemaname='public' AND tablename='result_issue_requests'
                      AND indexname LIKE 'ix_result_issue_journal_%'
                    ORDER BY indexname
                    """, String.class)).containsExactlyInAnyOrderElementsOf(JOURNAL_INDEXES);
        }
    }

    @Test
    void migratesAndValidatesAFreshDatabase() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getPostgresDatabase())
                    .target("23")
                    .load();
            flyway.migrate();
            flyway.validate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("23");
        }
    }
}
