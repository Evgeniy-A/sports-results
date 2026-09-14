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

class FlywayV13UpgradeTest {

    @Test
    void upgradesV12WithoutChangingExistingRegistrationsResultsOrInquirySettings() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("12").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            jdbc.update("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('V13 series', 'v13-series', TRUE, now(), now())
                    """);
            Long seriesId = jdbc.queryForObject(
                    "SELECT id FROM event_series WHERE slug='v13-series'", Long.class);
            jdbc.update("""
                    INSERT INTO events(
                        event_series_id, name, slug, starts_at, ends_at, time_zone,
                        publication_status, results_publication_status,
                        result_inquiry_enabled, result_inquiry_window_days, result_inquiry_email,
                        created_at, updated_at
                    ) VALUES (?, 'V13 event', 'v13-event', now() - interval '2 days',
                              now() - interval '1 day', 'Europe/Moscow', 'PUBLISHED', 'PUBLISHED',
                              TRUE, 76, 'timing@example.org', now(), now())
                    """, seriesId);
            Long eventId = jdbc.queryForObject("SELECT id FROM events WHERE slug='v13-event'", Long.class);
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
                    ) VALUES (?, 'EVENT', 'v13.csv', repeat('a', 64), 'SUCCEEDED',
                              1, 1, 0, 0, now(), now(), now(), now())
                    """, eventId);
            Long batchId = jdbc.queryForObject(
                    "SELECT id FROM import_batches WHERE event_id=?", Long.class, eventId);
            jdbc.update("""
                    INSERT INTO registrations(
                        race_id, import_batch_id, entry_kind, bib, display_name, birth_date,
                        source_row_number, source_row_hash, created_at, updated_at
                    ) VALUES (?, ?, 'PERSON', '817', 'Участник 817', DATE '1990-01-01',
                              2, repeat('b', 64), now(), now())
                    """, raceId, batchId);
            Long registrationId = jdbc.queryForObject(
                    "SELECT id FROM registrations WHERE race_id=?", Long.class, raceId);
            jdbc.update("""
                    INSERT INTO results(
                        registration_id, status, gun_time_ms, chip_time_ms, overall_place,
                        created_at, updated_at
                    ) VALUES (?, 'finished', 1000, 900, 1, now(), now())
                    """, registrationId);

            long registrationsBefore = jdbc.queryForObject("SELECT count(*) FROM registrations", Long.class);
            long resultsBefore = jdbc.queryForObject("SELECT count(*) FROM results", Long.class);
            Map<String, Object> registrationBefore = jdbc.queryForMap(
                    "SELECT bib, display_name, birth_date FROM registrations WHERE id=?", registrationId);
            Map<String, Object> resultBefore = jdbc.queryForMap(
                    "SELECT status, gun_time_ms, chip_time_ms, overall_place FROM results WHERE registration_id=?",
                    registrationId);
            Map<String, Object> inquirySettingsBefore = jdbc.queryForMap("""
                    SELECT result_inquiry_enabled, result_inquiry_window_days, result_inquiry_email
                    FROM events WHERE id=?
                    """, eventId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("13").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("13");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema='public' AND table_name='result_issue_requests'
                    """, Integer.class)).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema='public' AND table_name='result_issue_requests'
                      AND column_name='birth_date'
                    """, Integer.class)).isZero();
            String activeIndex = jdbc.queryForObject("""
                    SELECT pg_get_indexdef(indexrelid)
                    FROM pg_index
                    WHERE indexrelid='uk_result_issue_active_registration_type'::regclass
                    """, String.class);
            assertThat(activeIndex)
                    .contains("UNIQUE")
                    .contains("registration_id", "issue_type")
                    .contains("NEW", "IN_PROGRESS");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM result_issue_requests", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM registrations", Long.class))
                    .isEqualTo(registrationsBefore);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM results", Long.class))
                    .isEqualTo(resultsBefore);
            assertThat(jdbc.queryForMap(
                    "SELECT bib, display_name, birth_date FROM registrations WHERE id=?", registrationId))
                    .isEqualTo(registrationBefore);
            assertThat(jdbc.queryForMap(
                    "SELECT status, gun_time_ms, chip_time_ms, overall_place FROM results WHERE registration_id=?",
                    registrationId)).isEqualTo(resultBefore);
            assertThat(jdbc.queryForMap("""
                    SELECT result_inquiry_enabled, result_inquiry_window_days, result_inquiry_email
                    FROM events WHERE id=?
                    """, eventId)).isEqualTo(inquirySettingsBefore);

            jdbc.update("""
                    INSERT INTO result_issue_requests(
                        event_id, registration_id, issue_type, status, contact_email, message,
                        created_at, updated_at
                    ) VALUES (?, ?, 'MISSING_RESULT', 'NEW', 'runner@example.org', 'Первое обращение',
                              now(), now())
                    """, eventId, registrationId);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO result_issue_requests(
                        event_id, registration_id, issue_type, status, contact_email, message,
                        created_at, updated_at
                    ) VALUES (?, ?, 'MISSING_RESULT', 'IN_PROGRESS', 'runner@example.org',
                              'Конкурирующее обращение', now(), now())
                    """, eventId, registrationId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            jdbc.update("""
                    UPDATE result_issue_requests
                    SET status='RESOLVED', resolved_at=now(), updated_at=now()
                    WHERE registration_id=? AND issue_type='MISSING_RESULT'
                    """, registrationId);
            jdbc.update("""
                    INSERT INTO result_issue_requests(
                        event_id, registration_id, issue_type, status, contact_email, message,
                        created_at, updated_at
                    ) VALUES (?, ?, 'MISSING_RESULT', 'NEW', 'runner@example.org', 'Повторное обращение',
                              now(), now())
                    """, eventId, registrationId);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM result_issue_requests", Long.class)).isEqualTo(2);
        }
    }
}
