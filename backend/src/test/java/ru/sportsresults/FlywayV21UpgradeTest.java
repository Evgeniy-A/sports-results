package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayV21UpgradeTest {

    @Test
    void upgradesV20DataAndSupportsEmergencyRetirementAudit() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("20").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Seed seed = seed(jdbc);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("21").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("21");
            assertThat(jdbc.queryForMap(
                    "SELECT retired_at, retired_by_import_operation_id FROM registrations WHERE id=?",
                    seed.registrationId()
            )).containsEntry("retired_at", null).containsEntry("retired_by_import_operation_id", null);

            UUID operationId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO import_operations(
                        id, event_id, operation_mode, status, source_filename, file_sha256,
                        base_revision, plan_digest, created_by, preview_summary, created_at, updated_at
                    ) VALUES (?, ?, 'EMERGENCY_REPLACE', 'PREVIEWED', 'correct.csv', repeat('a', 64),
                              0, repeat('b', 64), 'migration-test', '{}'::jsonb, now(), now())
                    """, operationId, seed.eventId());
            jdbc.update("""
                    UPDATE registrations
                    SET retired_at=now(), retired_by_import_operation_id=?
                    WHERE id=?
                    """, operationId, seed.registrationId());
            jdbc.update("""
                    INSERT INTO import_operation_items(
                        operation_id, source_row_number, bib, decision, action,
                        registration_id, result_id, target_race_id, created_at, updated_at
                    ) VALUES (?, NULL, '1', 'RETIRED', 'RETIRE', ?, ?, ?, now(), now()),
                             (?, NULL, '2', 'RETIRED', 'RETIRE', NULL, NULL, ?, now(), now())
                    """, operationId, seed.registrationId(), seed.resultId(), seed.raceId(),
                    operationId, seed.raceId());
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM import_operation_items WHERE operation_id=? AND action='RETIRE'",
                    Integer.class, operationId
            )).isEqualTo(2);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE registrations SET retired_by_import_operation_id=NULL WHERE id=?",
                    seed.registrationId()
            )).hasStackTraceContaining("ck_registrations_retirement_metadata");
        }
    }

    @Test
    void migratesAndValidatesAFreshDatabase() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getPostgresDatabase())
                    .target("21")
                    .load();
            flyway.migrate();
            flyway.validate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("21");
        }
    }

    private static Seed seed(JdbcTemplate jdbc) {
        Long seriesId = jdbc.queryForObject("""
                INSERT INTO event_series(name, slug, active, created_at, updated_at)
                VALUES ('V21 series', 'v21-series', TRUE, now(), now()) RETURNING id
                """, Long.class);
        Long eventId = jdbc.queryForObject("""
                INSERT INTO events(
                    event_series_id, name, slug, starts_at, time_zone,
                    publication_status, results_publication_status, result_inquiry_enabled,
                    result_data_revision, created_at, updated_at
                ) VALUES (?, 'V21', 'v21', now(), 'UTC', 'PUBLISHED', 'PUBLISHED', FALSE, 0, now(), now())
                RETURNING id
                """, Long.class, seriesId);
        Long formatId = jdbc.queryForObject("""
                INSERT INTO sport_formats(event_id, display_name, display_order, public_visible, created_at, updated_at)
                VALUES (?, 'Individual', 0, TRUE, now(), now()) RETURNING id
                """, Long.class, eventId);
        Long raceId = jdbc.queryForObject("""
                INSERT INTO races(
                    event_id, sport_format_id, source_code, name, slug, display_order,
                    public_ranking_basis, public_visible, results_publication_status, created_at, updated_at
                ) VALUES (?, ?, '5 km', '5 km', '5-km', 0, 'GUN_TIME', TRUE, 'DRAFT', now(), now())
                RETURNING id
                """, Long.class, eventId, formatId);
        Long batchId = jdbc.queryForObject("""
                INSERT INTO import_batches(
                    event_id, scope_type, source_filename, file_sha256, status,
                    total_rows, imported_rows, skipped_rows, failed_rows,
                    started_at, finished_at, created_at, updated_at
                ) VALUES (?, 'EVENT', 'old.csv', repeat('c', 64), 'SUCCEEDED',
                          1, 1, 0, 0, now(), now(), now(), now()) RETURNING id
                """, Long.class, eventId);
        Long registrationId = jdbc.queryForObject("""
                INSERT INTO registrations(
                    race_id, import_batch_id, entry_kind, bib, display_name,
                    source_row_number, source_row_hash, created_at, updated_at
                ) VALUES (?, ?, 'PERSON', '1', 'Old runner', 2, repeat('d', 64), now(), now())
                RETURNING id
                """, Long.class, raceId, batchId);
        Long resultId = jdbc.queryForObject("""
                INSERT INTO results(registration_id, status, gun_time_ms, created_at, updated_at)
                VALUES (?, 'finished', 1000, now(), now()) RETURNING id
                """, Long.class, registrationId);
        return new Seed(eventId, raceId, registrationId, resultId);
    }

    private record Seed(Long eventId, Long raceId, Long registrationId, Long resultId) {
    }
}
