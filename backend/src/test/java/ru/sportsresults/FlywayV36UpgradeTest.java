package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV36UpgradeTest {

    @Test
    void addsHashedJournalShareGrantsAndSessionsWithoutChangingEarlierMigrations() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            var dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("35").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Flyway flyway = Flyway.configure().dataSource(dataSource).load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("36");
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema='public'
                      AND table_name IN (
                        'result_issue_journal_share_grants',
                        'result_issue_journal_share_sessions'
                      )
                    """, Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema='public'
                      AND table_name IN (
                        'result_issue_journal_share_grants',
                        'result_issue_journal_share_sessions'
                      )
                      AND column_name IN ('token_hash', 'session_token_hash')
                    """, Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                    WHERE table_schema='public'
                      AND table_name='result_issue_journal_share_grants'
                      AND column_name='token'
                    """, Integer.class)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM pg_constraint
                    WHERE conname IN (
                        'ck_result_issue_journal_share_sync_permission',
                        'ck_result_issue_journal_share_expiry',
                        'ck_result_issue_journal_share_revocation',
                        'ck_result_issue_journal_session_expiry'
                    )
                    """, Integer.class)).isEqualTo(4);
        }
    }
}
