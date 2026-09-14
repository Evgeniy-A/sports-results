package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV15UpgradeTest {

    @Test
    void upgradesV14WithMetadataOnlyAttachmentsAndPreservesExistingFacts() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("14").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('V15 series', 'v15-series', TRUE, now(), now()) RETURNING id
                    """, Long.class);
            Long eventId = jdbc.queryForObject("""
                    INSERT INTO events(
                        event_series_id, name, slug, starts_at, ends_at, time_zone,
                        publication_status, results_publication_status,
                        result_inquiry_enabled, result_inquiry_window_days, result_inquiry_email,
                        created_at, updated_at
                    ) VALUES (?, 'V15 event', 'v15-event', now() - interval '2 days',
                              now() - interval '1 day', 'Europe/Moscow', 'PUBLISHED', 'PUBLISHED',
                              TRUE, 76, 'timing@example.org', now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long formatId = jdbc.queryForObject("""
                    INSERT INTO sport_formats(
                        event_id, code, display_name, display_order, public_visible, created_at, updated_at
                    ) VALUES (?, 'individual', 'Индивидуальный', 0, TRUE, now(), now()) RETURNING id
                    """, Long.class, eventId);
            Long raceId = jdbc.queryForObject("""
                    INSERT INTO races(
                        event_id, sport_format_id, source_code, name, slug, display_order,
                        public_ranking_basis, public_visible, created_at, updated_at
                    ) VALUES (?, ?, '5 km', '5 км', '5-km', 0, 'GUN_TIME', TRUE, now(), now()) RETURNING id
                    """, Long.class, eventId, formatId);
            Long batchId = jdbc.queryForObject("""
                    INSERT INTO import_batches(
                        event_id, scope_type, source_filename, file_sha256, status,
                        total_rows, imported_rows, skipped_rows, failed_rows,
                        started_at, finished_at, created_at, updated_at
                    ) VALUES (?, 'EVENT', 'v15.csv', repeat('a', 64), 'SUCCEEDED',
                              1, 1, 0, 0, now(), now(), now(), now()) RETURNING id
                    """, Long.class, eventId);
            Long registrationId = jdbc.queryForObject("""
                    INSERT INTO registrations(
                        race_id, import_batch_id, entry_kind, bib, display_name, birth_date,
                        source_row_number, source_row_hash, created_at, updated_at
                    ) VALUES (?, ?, 'PERSON', '1100', 'Участник V15', DATE '1990-01-01',
                              2, repeat('b', 64), now(), now()) RETURNING id
                    """, Long.class, raceId, batchId);
            Long resultId = jdbc.queryForObject("""
                    INSERT INTO results(
                        registration_id, status, gun_time_ms, chip_time_ms, overall_place,
                        created_at, updated_at
                    ) VALUES (?, 'finished', 1000, 900, 1, now(), now()) RETURNING id
                    """, Long.class, registrationId);
            Long issueId = jdbc.queryForObject("""
                    INSERT INTO result_issue_requests(
                        event_id, registration_id, result_id, issue_type, correction_reason, status,
                        contact_email, message, observed_gun_time_ms, observed_chip_time_ms,
                        observed_result_status, created_at, updated_at
                    ) VALUES (?, ?, ?, 'RESULT_CORRECTION', 'OTHER', 'NEW',
                              'runner@example.org', 'Проверка V15', 1000, 900, 'finished', now(), now())
                    RETURNING id
                    """, Long.class, eventId, registrationId, resultId);

            Map<String, Object> registrationBefore = jdbc.queryForMap(
                    "SELECT bib, display_name, birth_date FROM registrations WHERE id=?", registrationId);
            Map<String, Object> resultBefore = jdbc.queryForMap("""
                    SELECT status, gun_time_ms, chip_time_ms, overall_place FROM results WHERE id=?
                    """, resultId);
            long issuesBefore = jdbc.queryForObject("SELECT count(*) FROM result_issue_requests", Long.class);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("15").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("15");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema='public' AND table_name='result_issue_attachments'
                    """, Integer.class)).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT data_type FROM information_schema.columns
                    WHERE table_schema='public' AND table_name='result_issue_attachments'
                      AND column_name='size_bytes'
                    """, String.class)).isEqualTo("bigint");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema='public' AND table_name='result_issue_attachments'
                      AND data_type='bytea'
                    """, Integer.class)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT attachment_upload_token_hash IS NULL
                           AND attachment_upload_token_expires_at IS NULL
                    FROM result_issue_requests WHERE id=?
                    """, Boolean.class, issueId)).isTrue();

            long largeSize = 5_000_000_000L;
            Long attachmentId = jdbc.queryForObject("""
                    INSERT INTO result_issue_attachments(
                        issue_request_id, original_file_name, storage_key, content_type, size_bytes,
                        upload_status, scan_status, upload_authorization_expires_at,
                        retention_expires_at, created_at, updated_at
                    ) VALUES (?, 'evidence.mp4', 'result-issues/test/object', 'video/mp4', ?,
                              'PENDING_UPLOAD', 'PENDING', now() + interval '15 minutes',
                              now() + interval '90 days', now(), now()) RETURNING id
                    """, Long.class, issueId, largeSize);
            assertThat(jdbc.queryForObject(
                    "SELECT size_bytes FROM result_issue_attachments WHERE id=?", Long.class, attachmentId))
                    .isEqualTo(largeSize);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM result_issue_attachments WHERE issue_request_id=?
                    """, Long.class, issueId)).isOne();

            assertThat(jdbc.queryForObject("SELECT count(*) FROM registrations", Long.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM results", Long.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM result_issue_requests", Long.class))
                    .isEqualTo(issuesBefore);
            assertThat(jdbc.queryForMap(
                    "SELECT bib, display_name, birth_date FROM registrations WHERE id=?", registrationId))
                    .isEqualTo(registrationBefore);
            assertThat(jdbc.queryForMap("""
                    SELECT status, gun_time_ms, chip_time_ms, overall_place FROM results WHERE id=?
                    """, resultId)).isEqualTo(resultBefore);
        }
    }
}
