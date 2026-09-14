package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV18UpgradeTest {

    @Test
    void addsUpdateCountersAndCreateResultAuditWithoutChangingSportsFacts() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("17").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('V18 series', 'v18-series', TRUE, now(), now()) RETURNING id
                    """, Long.class);
            Long eventId = jdbc.queryForObject("""
                    INSERT INTO events(
                        event_series_id, name, slug, time_zone, publication_status,
                        results_publication_status, result_inquiry_enabled, result_data_revision,
                        created_at, updated_at
                    ) VALUES (?, 'V18 event', 'v18-event', 'Europe/Moscow', 'PUBLISHED',
                              'PUBLISHED', FALSE, 7, now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long formatId = jdbc.queryForObject("""
                    INSERT INTO sport_formats(
                        event_id, code, display_name, display_order, public_visible, created_at, updated_at
                    ) VALUES (?, 'default', 'Default', 0, TRUE, now(), now()) RETURNING id
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
                    ) VALUES (?, 'EVENT', 'v18.csv', repeat('c', 64), 'SUCCEEDED',
                              1, 1, 0, 0, now(), now(), now(), now()) RETURNING id
                    """, Long.class, eventId);
            UUID operationId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO import_operations(
                        id, event_id, operation_mode, status, source_filename, file_sha256,
                        base_revision, plan_digest, created_by, preview_summary,
                        created_at, updated_at, expires_at, import_batch_id, applied_by, applied_at,
                        inserted_count, existing_skipped_count, out_of_scope_count, new_revision
                    ) VALUES (?, ?, 'ADD_NEW', 'APPLIED', 'v18.csv', repeat('c', 64),
                              6, repeat('d', 64), 'admin', '{}'::jsonb,
                              now(), now(), now() + interval '1 day', ?, 'admin', now(),
                              1, 1, 0, 7)
                    """, operationId, eventId, batchId);
            jdbc.update("""
                    INSERT INTO import_operation_items(
                        operation_id, source_row_number, bib, decision, action, target_race_id,
                        created_at, updated_at
                    ) VALUES (?, 2, '10', 'EXISTING_UNCHANGED', 'SKIP', ?, now(), now())
                    """, operationId, raceId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("18").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("18");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema='public' AND table_name='import_operations'
                      AND column_name IN (
                        'updated_count', 'result_created_count', 'new_skipped_count', 'unchanged_count'
                      )
                    """, Integer.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject(
                    "SELECT unchanged_count FROM import_operations WHERE id=?", Integer.class, operationId
            )).isOne();
            jdbc.update("""
                    INSERT INTO import_operation_items(
                        operation_id, source_row_number, bib, decision, action, target_race_id,
                        created_at, updated_at
                    ) VALUES (?, 3, '11', 'EXISTING_CHANGED', 'CREATE_RESULT', ?, now(), now())
                    """, operationId, raceId);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM import_operation_items WHERE operation_id=?", Integer.class, operationId
            )).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT result_data_revision FROM events WHERE id=?", Long.class, eventId
            )).isEqualTo(7);
        }
    }
}
