package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV35UpgradeTest {

    @Test
    void addsTypedRegistrationWorkStateWithoutBackfillingExistingRows() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            var dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("34").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Flyway flyway = Flyway.configure().dataSource(dataSource).load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("35");
            assertThat(jdbc.queryForList("""
                    SELECT column_name
                    FROM information_schema.columns
                    WHERE table_schema='public'
                      AND table_name='result_issue_work_items'
                      AND column_name IN (
                        'base_display_name', 'desired_display_name',
                        'base_gender', 'desired_gender',
                        'base_birth_date', 'desired_birth_date',
                        'base_bib', 'desired_bib',
                        'base_race_id', 'desired_race_id'
                      )
                    ORDER BY column_name
                    """, String.class)).hasSize(10);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                    FROM information_schema.columns
                    WHERE table_schema='public'
                      AND table_name='result_issue_work_items'
                      AND column_name IN ('base_race_id', 'desired_race_id')
                      AND is_nullable='YES'
                    """, Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                    FROM information_schema.table_constraints
                    WHERE table_schema='public'
                      AND table_name='result_issue_work_items'
                      AND constraint_name IN (
                        'fk_result_issue_work_items_base_race',
                        'fk_result_issue_work_items_desired_race'
                      )
                    """, Integer.class)).isEqualTo(2);
        }
    }
}
