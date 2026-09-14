package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV24UpgradeTest {

    @Test
    void upgradesV23WithRevocableHashedAttachmentShareGrants() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("23").load().migrate();

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("24").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("24");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForList("""
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema='public'
                      AND table_name IN (
                        'result_issue_share_batches',
                        'result_issue_attachment_share_grants'
                      )
                    ORDER BY table_name
                    """, String.class)).containsExactly(
                    "result_issue_attachment_share_grants",
                    "result_issue_share_batches"
            );
            assertThat(jdbc.queryForList("""
                    SELECT indexname
                    FROM pg_indexes
                    WHERE schemaname='public'
                      AND tablename='result_issue_attachment_share_grants'
                    """, String.class)).contains(
                    "uk_result_issue_share_grant_token_hash",
                    "uk_result_issue_share_grant_batch_attachment",
                    "ix_result_issue_share_grants_attachment",
                    "ix_result_issue_share_grants_expires_at"
            );
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                    FROM information_schema.columns
                    WHERE table_schema='public'
                      AND table_name='result_issue_attachment_share_grants'
                      AND column_name='token_hash'
                      AND character_maximum_length=64
                      AND is_nullable='NO'
                    """, Integer.class)).isOne();
            assertThat(jdbc.queryForList("""
                    SELECT pg_get_constraintdef(oid)
                    FROM pg_constraint
                    WHERE conname IN (
                      'fk_result_issue_share_grant_batch',
                      'fk_result_issue_share_grant_attachment'
                    )
                    """, String.class)).allSatisfy(definition ->
                    assertThat(definition).doesNotContain("ON DELETE CASCADE")
            );
        }
    }

    @Test
    void migratesAndValidatesAFreshDatabase() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            Flyway flyway = Flyway.configure().dataSource(postgres.getPostgresDatabase()).load();
            flyway.migrate();
            flyway.validate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("24");
        }
    }
}
