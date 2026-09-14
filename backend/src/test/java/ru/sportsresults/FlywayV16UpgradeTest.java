package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FlywayV16UpgradeTest {

    @Test
    void addsPreviewFoundationWithoutChangingExistingSportsFacts() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("15").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('V16 series', 'v16-series', TRUE, now(), now()) RETURNING id
                    """, Long.class);
            Long eventId = jdbc.queryForObject("""
                    INSERT INTO events(
                        event_series_id, name, slug, time_zone,
                        publication_status, results_publication_status,
                        result_inquiry_enabled, created_at, updated_at
                    ) VALUES (?, 'V16 event', 'v16-event', 'Europe/Moscow',
                              'PUBLISHED', 'PUBLISHED', FALSE, now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long formatId = jdbc.queryForObject("""
                    INSERT INTO sport_formats(
                        event_id, code, display_name, display_order, public_visible, created_at, updated_at
                    ) VALUES (?, 'individual', 'Individual', 0, TRUE, now(), now()) RETURNING id
                    """, Long.class, eventId);
            Long raceId = jdbc.queryForObject("""
                    INSERT INTO races(
                        event_id, sport_format_id, source_code, name, slug, display_order,
                        public_ranking_basis, public_visible, created_at, updated_at
                    ) VALUES (?, ?, '5 km', '5 km', '5-km', 0,
                              'CHIP_TIME', TRUE, now(), now()) RETURNING id
                    """, Long.class, eventId, formatId);
            Long batchId = jdbc.queryForObject("""
                    INSERT INTO import_batches(
                        event_id, scope_type, source_filename, file_sha256, status,
                        total_rows, imported_rows, skipped_rows, failed_rows,
                        started_at, finished_at, created_at, updated_at
                    ) VALUES (?, 'EVENT', 'before-v16.csv', repeat('a', 64), 'SUCCEEDED',
                              1, 1, 0, 0, now(), now(), now(), now()) RETURNING id
                    """, Long.class, eventId);
            Long registrationId = jdbc.queryForObject("""
                    INSERT INTO registrations(
                        race_id, import_batch_id, entry_kind, bib, display_name, birth_date,
                        source_row_number, source_row_hash, created_at, updated_at
                    ) VALUES (?, ?, 'PERSON', '0010', 'Runner', DATE '1990-01-01',
                              2, repeat('b', 64), now(), now()) RETURNING id
                    """, Long.class, raceId, batchId);
            Long resultId = jdbc.queryForObject("""
                    INSERT INTO results(
                        registration_id, status, gun_time_ms, chip_time_ms, overall_place,
                        created_at, updated_at
                    ) VALUES (?, 'finished', 1000, 900, 1, now(), now()) RETURNING id
                    """, Long.class, registrationId);
            Map<String, Object> registrationBefore = jdbc.queryForMap(
                    "SELECT race_id, bib, display_name, birth_date FROM registrations WHERE id=?", registrationId);
            Map<String, Object> resultBefore = jdbc.queryForMap(
                    "SELECT status, gun_time_ms, chip_time_ms, overall_place FROM results WHERE id=?", resultId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("16").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("16");
            assertThat(jdbc.queryForObject(
                    "SELECT result_data_revision FROM events WHERE id=?", Long.class, eventId)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema='public'
                      AND table_name IN ('import_operations', 'import_operation_races')
                    """, Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM pg_indexes
                    WHERE schemaname='public' AND indexname='uk_import_batches_successful_event_file'
                    """, Integer.class)).isZero();

            assertThatCode(() -> jdbc.update("""
                    INSERT INTO import_batches(
                        event_id, scope_type, source_filename, file_sha256, status,
                        total_rows, imported_rows, skipped_rows, failed_rows,
                        started_at, finished_at, created_at, updated_at
                    ) VALUES (?, 'EVENT', 'same-content-new-operation.csv', repeat('a', 64), 'SUCCEEDED',
                              1, 1, 0, 0, now(), now(), now(), now())
                    """, eventId)).doesNotThrowAnyException();

            UUID firstOperation = UUID.randomUUID();
            UUID secondOperation = UUID.randomUUID();
            for (UUID operationId : new UUID[]{firstOperation, secondOperation}) {
                jdbc.update("""
                        INSERT INTO import_operations(
                            id, event_id, operation_mode, status, source_filename, file_sha256,
                            base_revision, plan_digest, created_by, preview_summary,
                            created_at, updated_at, expires_at
                        ) VALUES (?, ?, 'ADD_NEW', 'PREVIEWED', 'preview.csv', repeat('c', 64),
                                  0, repeat('d', 64), 'admin', '{}'::jsonb,
                                  now(), now(), now() + interval '24 hours')
                        """, operationId, eventId);
                jdbc.update("INSERT INTO import_operation_races(operation_id, race_id) VALUES (?, ?)",
                        operationId, raceId);
            }
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM import_operations WHERE file_sha256=repeat('c', 64)", Long.class))
                    .isEqualTo(2);

            assertThat(jdbc.queryForMap(
                    "SELECT race_id, bib, display_name, birth_date FROM registrations WHERE id=?", registrationId))
                    .isEqualTo(registrationBefore);
            assertThat(jdbc.queryForMap(
                    "SELECT status, gun_time_ms, chip_time_ms, overall_place FROM results WHERE id=?", resultId))
                    .isEqualTo(resultBefore);
        }
    }
}
