package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayV26UpgradeTest {

    @Test
    void addsIndependentOptionalTemplatesWithoutChangingExistingSportsData() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("25").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('Existing series', 'existing-series', true, now(), now()) RETURNING id
                    """, Long.class);
            Long eventId = jdbc.queryForObject("""
                    INSERT INTO events(event_series_id, name, slug, created_at, updated_at)
                    VALUES (?, 'Existing event', 'existing-event', now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long raceId = jdbc.queryForObject("""
                    INSERT INTO races(event_id, source_code, name, slug, display_order, created_at, updated_at)
                    VALUES (?, '10K', '10 км', '10-km', 0, now(), now()) RETURNING id
                    """, Long.class, eventId);
            Long policyId = jdbc.queryForObject("""
                    INSERT INTO award_policies(
                        race_id, ranking_basis, primary_standing_mode, absolute_prize_places,
                        category_enabled, category_prize_places, exclude_absolute_winners_from_category,
                        created_at, updated_at
                    ) VALUES (?, 'GUN_TIME', 'BY_GENDER', 3, false, 0, false, now(), now()) RETURNING id
                    """, Long.class, raceId);
            Long categoryId = jdbc.queryForObject("""
                    INSERT INTO categories(
                        race_id, source_name, display_name, display_order,
                        min_age, max_age, gender, enabled, created_at, updated_at
                    ) VALUES (?, '18-29', '18–29', 0, 18, 29, NULL, true, now(), now()) RETURNING id
                    """, Long.class, raceId);
            Long batchId = jdbc.queryForObject("""
                    INSERT INTO import_batches(
                        event_id, race_id, scope_type, source_filename, file_sha256, status,
                        started_at, created_at, updated_at
                    ) VALUES (?, ?, 'RACE', 'existing.csv', repeat('a', 64), 'SUCCEEDED',
                              now(), now(), now()) RETURNING id
                    """, Long.class, eventId, raceId);
            Long registrationId = jdbc.queryForObject("""
                    INSERT INTO registrations(
                        race_id, import_batch_id, entry_kind, bib, display_name,
                        source_row_number, source_row_hash, created_at, updated_at
                    ) VALUES (?, ?, 'PERSON', '42', 'Synthetic Runner', 2, repeat('b', 64), now(), now())
                    RETURNING id
                    """, Long.class, raceId, batchId);
            Long resultId = jdbc.queryForObject("""
                    INSERT INTO results(registration_id, status, gun_time_ms, chip_time_ms, created_at, updated_at)
                    VALUES (?, 'finished', 1234, 1200, now(), now()) RETURNING id
                    """, Long.class, registrationId);

            Map<String, Object> raceBefore = jdbc.queryForMap("SELECT * FROM races WHERE id=?", raceId);
            Map<String, Object> policyBefore = jdbc.queryForMap("SELECT * FROM award_policies WHERE id=?", policyId);
            Map<String, Object> registrationBefore = jdbc.queryForMap(
                    "SELECT * FROM registrations WHERE id=?", registrationId);
            Map<String, Object> resultBefore = jdbc.queryForMap("SELECT * FROM results WHERE id=?", resultId);
            Map<String, Object> categoryBefore = jdbc.queryForMap("SELECT * FROM categories WHERE id=?", categoryId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("26").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("26");
            assertThat(tableExists(jdbc, "event_series_start_templates")).isTrue();
            assertThat(tableExists(jdbc, "event_series_start_award_policy_templates")).isTrue();
            assertThat(tableExists(jdbc, "event_series_start_category_templates")).isTrue();
            assertThat(columnExists(jdbc, "event_series_start_templates", "source_code")).isFalse();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM event_series_start_templates WHERE event_series_id=?",
                    Long.class, seriesId)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM event_series_start_category_templates",
                    Long.class)).isZero();
            assertThat(jdbc.queryForMap("SELECT * FROM races WHERE id=?", raceId)).isEqualTo(raceBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM award_policies WHERE id=?", policyId)).isEqualTo(policyBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM registrations WHERE id=?", registrationId))
                    .isEqualTo(registrationBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM results WHERE id=?", resultId)).isEqualTo(resultBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM categories WHERE id=?", categoryId)).isEqualTo(categoryBefore);

            Long templateStartId = jdbc.queryForObject("""
                    INSERT INTO event_series_start_templates(
                        event_series_id, name, distance_meters, display_order,
                        public_visible, created_at, updated_at
                    ) VALUES (?, '5 км', 5000, 0, true, now(), now()) RETURNING id
                    """, Long.class, seriesId);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM event_series_start_award_policy_templates WHERE template_start_id=?",
                    Long.class, templateStartId)).isZero();
            jdbc.update("""
                    INSERT INTO event_series_start_award_policy_templates(
                        template_start_id, ranking_basis, primary_standing_mode, absolute_prize_places,
                        category_enabled, age_calculation_mode, category_prize_places,
                        exclude_absolute_winners_from_category, created_at, updated_at
                    ) VALUES (?, 'CHIP_TIME', 'ALL', 3, false, 'EVENT_DATE', 0, false, now(), now())
                    """, templateStartId);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM event_series_start_award_policy_templates WHERE template_start_id=?",
                    Long.class, templateStartId)).isEqualTo(1);
            Long templateCategoryId = jdbc.queryForObject("""
                    INSERT INTO event_series_start_category_templates(
                        template_start_id, source_name, display_name, display_order,
                        min_age, max_age, gender, enabled, created_at, updated_at
                    ) VALUES (?, '30-39', '30–39', 0, 30, 39, NULL, true, now(), now()) RETURNING id
                    """, Long.class, templateStartId);
            assertThat(templateCategoryId).isNotNull();

            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO event_series_start_templates(
                        event_series_id, name, display_order, created_at, updated_at
                    ) VALUES (999999, 'Foreign', 0, now(), now())
                    """))
                    .isInstanceOf(DataIntegrityViolationException.class);

            jdbc.update("DELETE FROM event_series_start_templates WHERE id=?", templateStartId);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM event_series_start_category_templates WHERE id=?",
                    Long.class, templateCategoryId)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM races WHERE id=?", Long.class, raceId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE id=?", Long.class, eventId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM award_policies WHERE id=?", Long.class, policyId))
                    .isEqualTo(1);
        }
    }

    private static boolean tableExists(JdbcTemplate jdbc, String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*) > 0 FROM information_schema.tables
                WHERE table_schema='public' AND table_name=?
                """, Boolean.class, table));
    }

    private static boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT count(*) > 0 FROM information_schema.columns
                WHERE table_schema='public' AND table_name=? AND column_name=?
                """, Boolean.class, table, column));
    }
}
