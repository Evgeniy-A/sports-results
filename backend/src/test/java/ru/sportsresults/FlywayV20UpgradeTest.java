package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayV20UpgradeTest {

    @Test
    void backfillsLegacySnapshotAndReplacesTheActiveIssueIndex() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("19").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Seed seed = seed(jdbc, "upgrade");
            Long missingRegistrationId = jdbc.queryForObject("""
                    INSERT INTO registrations(
                        race_id, import_batch_id, entry_kind, bib, display_name,
                        source_row_number, source_row_hash, created_at, updated_at
                    ) VALUES (?, ?, 'PERSON', 'V20-2', 'Legacy Missing Result',
                              3, repeat('c', 64), now(), now()) RETURNING id
                    """, Long.class, seed.raceId(), seed.batchId());
            Long missingIssueId = jdbc.queryForObject("""
                    INSERT INTO result_issue_requests(
                        event_id, registration_id, issue_type, status,
                        contact_email, message, created_at, updated_at
                    ) VALUES (?, ?, 'MISSING_RESULT', 'NEW',
                              'missing@example.org', 'Legacy missing result', now(), now())
                    RETURNING id
                    """, Long.class, seed.eventId(), missingRegistrationId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("20").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("20");
            var snapshot = jdbc.queryForMap("""
                    SELECT snapshot_origin, snapshot_event_name, snapshot_event_location,
                           snapshot_event_starts_at, snapshot_sport_format_id,
                           snapshot_sport_format_name, snapshot_sport_format_code,
                           snapshot_race_id, snapshot_race_name, snapshot_race_code,
                           snapshot_race_distance_meters, snapshot_bib, snapshot_display_name,
                           snapshot_effective_category_name, snapshot_source_category,
                           snapshot_category_publicly_enabled, snapshot_ranking,
                           snapshot_import_batch_id, snapshot_source_row_number,
                           queue_archived_at, queue_archived_by, queue_archive_reason
                    FROM result_issue_requests WHERE id = ?
                    """, seed.issueId());
            assertThat(snapshot)
                    .containsEntry("snapshot_origin", "LEGACY_BACKFILL_CURRENT_STATE")
                    .containsEntry("snapshot_event_name", "V20 upgrade")
                    .containsEntry("snapshot_event_location", "Perm")
                    .containsEntry("snapshot_sport_format_name", "Individual")
                    .containsEntry("snapshot_sport_format_code", "individual")
                    .containsEntry("snapshot_race_name", "10 km")
                    .containsEntry("snapshot_race_code", "10 km")
                    .containsEntry("snapshot_bib", "V20-1")
                    .containsEntry("snapshot_display_name", "Legacy Runner")
                    .containsEntry("snapshot_effective_category_name", "18+ Men")
                    .containsEntry("snapshot_source_category", "18+ Male")
                    .containsEntry("snapshot_category_publicly_enabled", true)
                    .containsEntry("snapshot_source_row_number", 2);
            assertThat(snapshot.get("snapshot_event_starts_at")).isNotNull();
            assertThat(((Number) snapshot.get("snapshot_sport_format_id")).longValue())
                    .isEqualTo(seed.formatId());
            assertThat(((Number) snapshot.get("snapshot_race_id")).longValue()).isEqualTo(seed.raceId());
            assertThat(((Number) snapshot.get("snapshot_import_batch_id")).longValue()).isEqualTo(seed.batchId());
            assertThat(snapshot.get("snapshot_ranking")).isNull();
            assertThat(snapshot.get("queue_archived_at")).isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM result_issue_history", Integer.class)).isZero();
            assertThat(jdbc.queryForMap("""
                    SELECT snapshot_origin, snapshot_race_id, snapshot_bib, snapshot_display_name,
                           snapshot_effective_category_name, snapshot_source_category, snapshot_ranking
                    FROM result_issue_requests WHERE id=?
                    """, missingIssueId))
                    .containsEntry("snapshot_origin", "LEGACY_BACKFILL_CURRENT_STATE")
                    .containsEntry("snapshot_bib", "V20-2")
                    .containsEntry("snapshot_display_name", "Legacy Missing Result")
                    .containsEntry("snapshot_effective_category_name", null)
                    .containsEntry("snapshot_source_category", null)
                    .containsEntry("snapshot_ranking", null);

            String indexDefinition = jdbc.queryForObject("""
                    SELECT indexdef FROM pg_indexes
                    WHERE schemaname = 'public' AND indexname = 'uk_result_issue_active_registration'
                    """, String.class);
            assertThat(indexDefinition)
                    .contains("status")
                    .contains("queue_archived_at IS NULL");

            jdbc.update("""
                    UPDATE result_issue_requests
                    SET queue_archived_at=now(), queue_archived_by='migration-test',
                        queue_archive_reason='MANUAL'
                    WHERE id=?
                    """, seed.issueId());
            insertIssue(jdbc, seed, "MISSING_RESULT", null, null);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM result_issue_requests WHERE registration_id=?",
                    Integer.class, seed.registrationId()
            )).isEqualTo(2);
            assertThatThrownBy(() -> insertIssue(jdbc, seed, "MISSING_RESULT", null, null))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void failsLoudlyWhenConflictingActiveRowsExistBeforeIndexReplacement() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("19").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Seed seed = seed(jdbc, "conflict");
            jdbc.execute("DROP INDEX uk_result_issue_active_registration");
            insertLegacyIssue(jdbc, seed);

            assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource).target("20").load().migrate())
                    .hasStackTraceContaining("Cannot enforce one non-archived active result issue");
        }
    }

    @Test
    void migratesAndValidatesAFreshDatabase() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            Flyway flyway = Flyway.configure().dataSource(postgres.getPostgresDatabase()).target("20").load();
            flyway.migrate();
            flyway.validate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("20");
        }
    }

    private static Seed seed(JdbcTemplate jdbc, String slug) {
        Long seriesId = jdbc.queryForObject("""
                INSERT INTO event_series(name, slug, active, created_at, updated_at)
                VALUES ('V20 series', ?, TRUE, now(), now()) RETURNING id
                """, Long.class, "v20-" + slug + "-series");
        Long eventId = jdbc.queryForObject("""
                INSERT INTO events(
                    event_series_id, name, slug, starts_at, location, time_zone,
                    publication_status, results_publication_status, result_inquiry_enabled,
                    result_data_revision, created_at, updated_at
                ) VALUES (?, ?, ?, '2026-09-01T05:00:00Z'::timestamptz, 'Perm', 'Asia/Yekaterinburg',
                          'PUBLISHED', 'PUBLISHED', FALSE, 3, now(), now()) RETURNING id
                """, Long.class, seriesId, "V20 " + slug, "v20-" + slug);
        Long formatId = jdbc.queryForObject("""
                INSERT INTO sport_formats(
                    event_id, code, display_name, display_order, public_visible, created_at, updated_at
                ) VALUES (?, 'individual', 'Individual', 0, TRUE, now(), now()) RETURNING id
                """, Long.class, eventId);
        Long raceId = jdbc.queryForObject("""
                INSERT INTO races(
                    event_id, sport_format_id, source_code, name, slug, distance_meters,
                    display_order, public_ranking_basis, public_visible,
                    results_publication_status, created_at, updated_at
                ) VALUES (?, ?, '10 km', '10 km', '10-km', 10000,
                          0, 'GUN_TIME', TRUE, 'PUBLISHED', now(), now()) RETURNING id
                """, Long.class, eventId, formatId);
        Long categoryId = jdbc.queryForObject("""
                INSERT INTO categories(
                    race_id, source_name, display_name, display_order, min_age, gender, enabled,
                    created_at, updated_at
                ) VALUES (?, '18+ Male', '18+ Men', 0, 18, 'MALE', TRUE, now(), now()) RETURNING id
                """, Long.class, raceId);
        jdbc.update("""
                INSERT INTO award_policies(
                    race_id, ranking_basis, primary_standing_mode, absolute_prize_places,
                    category_enabled, age_calculation_mode, category_prize_places,
                    exclude_absolute_winners_from_category, created_at, updated_at
                ) VALUES (?, 'GUN_TIME', 'ALL', 3, TRUE, 'EVENT_DATE', 3, FALSE, now(), now())
                """, raceId);
        Long batchId = jdbc.queryForObject("""
                INSERT INTO import_batches(
                    event_id, scope_type, source_filename, file_sha256, status,
                    total_rows, imported_rows, skipped_rows, failed_rows,
                    started_at, finished_at, created_at, updated_at
                ) VALUES (?, 'EVENT', 'v20.csv', repeat('a', 64), 'SUCCEEDED',
                          1, 1, 0, 0, now(), now(), now(), now()) RETURNING id
                """, Long.class, eventId);
        Long registrationId = jdbc.queryForObject("""
                INSERT INTO registrations(
                    race_id, category_id, import_batch_id, entry_kind, bib, display_name,
                    source_category, gender, source_row_number, source_row_hash,
                    created_at, updated_at
                ) VALUES (?, ?, ?, 'PERSON', 'V20-1', 'Legacy Runner', '18+ Male', 'male',
                          2, repeat('b', 64), now(), now()) RETURNING id
                """, Long.class, raceId, categoryId, batchId);
        Long resultId = jdbc.queryForObject("""
                INSERT INTO results(
                    registration_id, status, gun_time_ms, chip_time_ms, created_at, updated_at
                ) VALUES (?, 'finished', 5000, 4900, now(), now()) RETURNING id
                """, Long.class, registrationId);
        Long issueId = jdbc.queryForObject("""
                INSERT INTO result_issue_requests(
                    event_id, registration_id, result_id, issue_type, correction_reason, status,
                    contact_email, message, observed_gun_time_ms, observed_chip_time_ms,
                    observed_result_status, created_at, updated_at
                ) VALUES (?, ?, ?, 'RESULT_CORRECTION', 'OFFICIAL_TIME', 'NEW',
                          'legacy@example.org', 'Legacy issue', 5000, 4900, 'finished', now(), now())
                RETURNING id
                """, Long.class, eventId, registrationId, resultId);
        return new Seed(eventId, formatId, raceId, batchId, registrationId, resultId, issueId);
    }

    private static void insertIssue(
            JdbcTemplate jdbc,
            Seed seed,
            String type,
            String correctionReason,
            String observedStatus
    ) {
        jdbc.update("""
                INSERT INTO result_issue_requests(
                    event_id, registration_id, result_id, issue_type, correction_reason, status,
                    contact_email, message, observed_result_status, snapshot_origin,
                    created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'NEW', 'new@example.org', 'New issue', ?,
                          'CAPTURED_AT_CREATION', now(), now())
                """, seed.eventId(), seed.registrationId(),
                "RESULT_CORRECTION".equals(type) ? seed.resultId() : null,
                type, correctionReason, observedStatus);
    }

    private static void insertLegacyIssue(JdbcTemplate jdbc, Seed seed) {
        jdbc.update("""
                INSERT INTO result_issue_requests(
                    event_id, registration_id, issue_type, status,
                    contact_email, message, created_at, updated_at
                ) VALUES (?, ?, 'MISSING_RESULT', 'NEW',
                          'legacy-conflict@example.org', 'Conflicting issue', now(), now())
                """, seed.eventId(), seed.registrationId());
    }

    private record Seed(
            Long eventId,
            Long formatId,
            Long raceId,
            Long batchId,
            Long registrationId,
            Long resultId,
            Long issueId
    ) {
    }
}
