package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV34UpgradeTest {

    @Test
    void addsResultIssueWorkItemsWithoutChangingExistingClaimsOrResults() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            var dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("33").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(tableCount(jdbc, "result_issue_work_items")).isZero();

            Flyway flyway = Flyway.configure().dataSource(dataSource).load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("35");
            assertThat(tableCount(jdbc, "result_issue_work_items")).isOne();
            assertThat(columnCount(jdbc, "result_issue_requests", "claimed_gun_time_ms")).isOne();
            assertThat(columnCount(jdbc, "results", "gun_time_ms")).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                    FROM information_schema.table_constraints
                    WHERE table_schema='public'
                      AND table_name='result_issue_work_items'
                      AND constraint_type='UNIQUE'
                    """, Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("""
                    SELECT rc.delete_rule
                    FROM information_schema.referential_constraints rc
                    JOIN information_schema.table_constraints tc
                      ON tc.constraint_catalog=rc.constraint_catalog
                     AND tc.constraint_schema=rc.constraint_schema
                     AND tc.constraint_name=rc.constraint_name
                    WHERE tc.table_schema='public'
                      AND tc.table_name='result_issue_work_items'
                      AND tc.constraint_name='fk_result_issue_work_items_result'
                    """, String.class)).isEqualTo("SET NULL");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                    FROM pg_indexes
                    WHERE schemaname='public'
                      AND tablename='result_issue_work_items'
                      AND indexname IN (
                        'ix_result_issue_work_items_pending',
                        'ix_result_issue_work_items_result',
                        'ix_result_issue_work_items_assignee'
                      )
                    """, Integer.class)).isEqualTo(3);
        }
    }

    private static Integer tableCount(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema='public' AND table_name=?
                """, Integer.class, tableName);
    }

    private static Integer columnCount(JdbcTemplate jdbc, String tableName, String columnName) {
        return jdbc.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema='public' AND table_name=? AND column_name=?
                """, Integer.class, tableName, columnName);
    }
}
