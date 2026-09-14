package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV12UpgradeTest {

    @Test
    void upgradesV11DataWithInquiryDisabledAndWithoutChangingSportingFacts() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("11").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            jdbc.update("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('Upgrade series', 'upgrade-series', TRUE, now(), now())
                    """);
            Long seriesId = jdbc.queryForObject(
                    "SELECT id FROM event_series WHERE slug='upgrade-series'", Long.class);
            jdbc.update("""
                    INSERT INTO events(
                        event_series_id, name, slug, starts_at, ends_at, time_zone,
                        publication_status, results_publication_status, created_at, updated_at
                    ) VALUES (?, 'Upgrade event', 'upgrade-event', now() - interval '2 days',
                              now() - interval '1 day', 'Europe/Moscow',
                              'PUBLISHED', 'PUBLISHED', now(), now())
                    """, seriesId);
            Long eventId = jdbc.queryForObject("SELECT id FROM events WHERE slug='upgrade-event'", Long.class);
            jdbc.update("""
                    INSERT INTO sport_formats(
                        event_id, code, display_name, display_order, public_visible, created_at, updated_at
                    ) VALUES (?, 'individual', 'Индивидуальный', 0, TRUE, now(), now())
                    """, eventId);
            Long formatId = jdbc.queryForObject(
                    "SELECT id FROM sport_formats WHERE event_id=?", Long.class, eventId);
            jdbc.update("""
                    INSERT INTO races(
                        event_id, sport_format_id, source_code, name, slug, display_order,
                        public_ranking_basis, public_visible, created_at, updated_at
                    ) VALUES (?, ?, '5 km', '5 км', '5-km', 0, 'GUN_TIME', TRUE, now(), now())
                    """, eventId, formatId);
            Long raceId = jdbc.queryForObject("SELECT id FROM races WHERE event_id=?", Long.class, eventId);
            jdbc.update("""
                    INSERT INTO import_batches(
                        event_id, scope_type, source_filename, file_sha256, status,
                        total_rows, imported_rows, skipped_rows, failed_rows,
                        started_at, finished_at, created_at, updated_at
                    ) VALUES (?, 'EVENT', 'upgrade.csv', repeat('a', 64), 'SUCCEEDED',
                              1, 1, 0, 0, now(), now(), now(), now())
                    """, eventId);
            Long batchId = jdbc.queryForObject(
                    "SELECT id FROM import_batches WHERE event_id=?", Long.class, eventId);
            jdbc.update("""
                    INSERT INTO registrations(
                        race_id, import_batch_id, entry_kind, bib, display_name,
                        source_row_number, source_row_hash, created_at, updated_at
                    ) VALUES (?, ?, 'PERSON', '817', 'Участник 817', 2, repeat('b', 64), now(), now())
                    """, raceId, batchId);
            Long registrationId = jdbc.queryForObject(
                    "SELECT id FROM registrations WHERE race_id=?", Long.class, raceId);
            jdbc.update("""
                    INSERT INTO results(
                        registration_id, status, gun_time_ms, chip_time_ms, overall_place,
                        created_at, updated_at
                    ) VALUES (?, 'finished', 1000, 900, 1, now(), now())
                    """, registrationId);
            jdbc.update("""
                    INSERT INTO award_policies(
                        race_id, ranking_basis, primary_standing_mode, absolute_prize_places,
                        category_enabled, age_calculation_mode, category_prize_places,
                        exclude_absolute_winners_from_category, created_at, updated_at
                    ) VALUES (?, 'GUN_TIME', 'ALL', 3, FALSE, 'EVENT_DATE', 0, FALSE, now(), now())
                    """, raceId);

            long registrationsBefore = jdbc.queryForObject("SELECT count(*) FROM registrations", Long.class);
            long resultsBefore = jdbc.queryForObject("SELECT count(*) FROM results", Long.class);
            Map<String, Object> resultBefore = jdbc.queryForMap(
                    "SELECT status, gun_time_ms, chip_time_ms, overall_place FROM results WHERE registration_id=?",
                    registrationId);
            Map<String, Object> policyBefore = jdbc.queryForMap(
                    "SELECT ranking_basis, primary_standing_mode, absolute_prize_places FROM award_policies WHERE race_id=?",
                    raceId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("12").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("12");
            assertThat(jdbc.queryForObject(
                    "SELECT result_inquiry_enabled FROM events WHERE id=?", Boolean.class, eventId)).isFalse();
            assertThat(jdbc.queryForObject(
                    "SELECT result_inquiry_window_days FROM events WHERE id=?", Integer.class, eventId)).isNull();
            assertThat(jdbc.queryForObject(
                    "SELECT result_inquiry_email FROM events WHERE id=?", String.class, eventId)).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM registrations", Long.class))
                    .isEqualTo(registrationsBefore);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM results", Long.class))
                    .isEqualTo(resultsBefore);
            assertThat(jdbc.queryForMap(
                    "SELECT status, gun_time_ms, chip_time_ms, overall_place FROM results WHERE registration_id=?",
                    registrationId)).isEqualTo(resultBefore);
            assertThat(jdbc.queryForMap(
                    "SELECT ranking_basis, primary_standing_mode, absolute_prize_places FROM award_policies WHERE race_id=?",
                    raceId)).isEqualTo(policyBefore);
            assertThat(jdbc.queryForObject("SELECT public_visible FROM races WHERE id=?", Boolean.class, raceId)).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT public_visible FROM sport_formats WHERE id=?", Boolean.class, formatId)).isTrue();
        }
    }
}
