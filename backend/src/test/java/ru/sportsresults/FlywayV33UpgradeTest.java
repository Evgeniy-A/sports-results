package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV33UpgradeTest {

    @Test
    void addsOptionalInfoBlockLinkWithSetNullDeletion() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            var dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("32").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(columnCount(jdbc, "info_block_id")).isZero();

            Flyway flyway = Flyway.configure().dataSource(dataSource).load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("34");
            assertThat(columnCount(jdbc, "info_block_id")).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT is_nullable
                    FROM information_schema.columns
                    WHERE table_schema='public' AND table_name='event_documents' AND column_name='info_block_id'
                    """, String.class)).isEqualTo("YES");
            assertThat(jdbc.queryForObject("""
                    SELECT rc.delete_rule
                    FROM information_schema.referential_constraints rc
                    JOIN information_schema.table_constraints tc
                      ON tc.constraint_catalog=rc.constraint_catalog
                     AND tc.constraint_schema=rc.constraint_schema
                     AND tc.constraint_name=rc.constraint_name
                    WHERE tc.table_schema='public'
                      AND tc.table_name='event_documents'
                      AND tc.constraint_name='fk_event_documents_info_block'
                    """, String.class)).isEqualTo("SET NULL");
        }
    }

    private static Integer columnCount(JdbcTemplate jdbc, String columnName) {
        return jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema='public' AND table_name='event_documents' AND column_name=?
                """, Integer.class, columnName);
    }
}
