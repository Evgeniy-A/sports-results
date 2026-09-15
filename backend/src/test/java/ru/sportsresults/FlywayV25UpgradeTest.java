package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayV25UpgradeTest {

    @Test
    void materializesRacePresentationAndPreservesSportsDataAndForeignKeys() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            migrateToV24(dataSource);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Seed seed = seedRepresentativeData(jdbc);

            Map<String, Object> raceBefore = jdbc.queryForMap(
                    "SELECT id, event_id, source_code, slug FROM races WHERE id=?", seed.defaultRaceId());
            Map<String, Object> registrationBefore = jdbc.queryForMap(
                    "SELECT * FROM registrations WHERE id=?", seed.registrationId());
            Map<String, Object> resultBefore = jdbc.queryForMap(
                    "SELECT * FROM results WHERE id=?", seed.resultId());
            Map<String, Object> awardBefore = jdbc.queryForMap(
                    "SELECT * FROM award_policies WHERE race_id=?", seed.defaultRaceId());
            Map<String, Object> categoryBefore = jdbc.queryForMap(
                    "SELECT * FROM categories WHERE id=?", seed.categoryId());
            Map<String, Object> clusterBefore = jdbc.queryForMap(
                    "SELECT * FROM start_clusters WHERE id=?", seed.clusterId());
            Map<String, Object> checkpointBefore = jdbc.queryForMap(
                    "SELECT * FROM checkpoints WHERE id=?", seed.checkpointId());
            Map<String, Object> splitBefore = jdbc.queryForMap(
                    "SELECT * FROM splits WHERE id=?", seed.splitId());

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("25").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("25");
            assertThat(jdbc.queryForMap(
                    "SELECT id, event_id, source_code, slug FROM races WHERE id=?", seed.defaultRaceId()))
                    .isEqualTo(raceBefore);
            assertThat(jdbc.queryForList(
                    "SELECT id, name, public_visible, display_order FROM races WHERE event_id=? ORDER BY display_order",
                    seed.eventId()))
                    .containsExactly(
                            Map.of("id", seed.massPrefixedRaceId(), "name", "Масс-старт   20 км",
                                    "public_visible", false, "display_order", 0),
                            Map.of("id", seed.massRaceId(), "name", "Масс-старт 10 км",
                                    "public_visible", false, "display_order", 1),
                            Map.of("id", seed.champRaceId(), "name", "Чемпионат",
                                    "public_visible", true, "display_order", 2),
                            Map.of("id", seed.defaultRaceId(), "name", "10 км",
                                    "public_visible", true, "display_order", 3)
                    );

            assertThat(tableExists(jdbc, "sport_formats")).isFalse();
            assertThat(columnExists(jdbc, "races", "sport_format_id")).isFalse();
            assertThat(columnExists(jdbc, "races", "entry_mode")).isFalse();
            assertThat(jdbc.queryForMap("SELECT * FROM registrations WHERE id=?", seed.registrationId()))
                    .isEqualTo(registrationBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM results WHERE id=?", seed.resultId()))
                    .isEqualTo(resultBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM award_policies WHERE race_id=?", seed.defaultRaceId()))
                    .isEqualTo(awardBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM categories WHERE id=?", seed.categoryId()))
                    .isEqualTo(categoryBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM start_clusters WHERE id=?", seed.clusterId()))
                    .isEqualTo(clusterBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM checkpoints WHERE id=?", seed.checkpointId()))
                    .isEqualTo(checkpointBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM splits WHERE id=?", seed.splitId()))
                    .isEqualTo(splitBefore);

            assertThat(jdbc.queryForMap("""
                    SELECT snapshot_sport_format_id, snapshot_sport_format_name, snapshot_sport_format_code,
                           snapshot_race_id, snapshot_race_name, snapshot_race_code
                    FROM result_issue_requests WHERE id=?
                    """, seed.issueId())).containsAllEntriesOf(Map.of(
                    "snapshot_sport_format_id", seed.defaultFormatId(),
                    "snapshot_sport_format_name", "Основной формат",
                    "snapshot_sport_format_code", "default",
                    "snapshot_race_id", seed.defaultRaceId(),
                    "snapshot_race_name", " 10 км ",
                    "snapshot_race_code", "10 km"
            ));
            assertThat(jdbc.queryForList("""
                    SELECT tc.table_name
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.constraint_column_usage ccu
                      ON ccu.constraint_schema=tc.constraint_schema AND ccu.constraint_name=tc.constraint_name
                    WHERE tc.constraint_schema='public' AND tc.constraint_type='FOREIGN KEY'
                      AND ccu.table_name='races'
                    ORDER BY tc.table_name
                    """, String.class)).contains(
                    "award_policies", "categories", "checkpoints", "import_batches",
                    "import_operation_items", "import_operation_races", "race_result_publication_history",
                    "registrations", "result_recalculation_operation_races", "start_clusters"
            );
            assertThat(jdbc.queryForObject(
                    "SELECT race_id FROM import_batches WHERE id=?", Long.class, seed.importBatchId()))
                    .isEqualTo(seed.defaultRaceId());
        }
    }

    @Test
    void rejectsActiveImportPreviewOrApply() throws Exception {
        for (String status : List.of("PREVIEWED", "APPLYING")) {
            try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
                DataSource dataSource = postgres.getPostgresDatabase();
                migrateToV24(dataSource);
                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                Seed seed = seedRepresentativeData(jdbc);
                jdbc.update("""
                        INSERT INTO import_operations(
                            id, event_id, operation_mode, status, source_filename, file_sha256,
                            base_revision, plan_digest, created_by, preview_summary, created_at, updated_at
                        ) VALUES (?, ?, 'ADD_NEW', ?, 'pending.csv', repeat('a', 64), 0,
                                  repeat('b', 64), 'migration-test', '{}'::jsonb, now(), now())
                        """, UUID.randomUUID(), seed.eventId(), status);

                assertMigrationFails(dataSource, "PREVIEWED or APPLYING imports exist");
                assertThat(tableExists(jdbc, "sport_formats")).isTrue();
            }
        }
    }

    @Test
    void rejectsDuplicateProjectedRaceNames() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            migrateToV24(dataSource);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Seed seed = seedRepresentativeData(jdbc);
            jdbc.update("UPDATE races SET name='10 КМ' WHERE id=?", seed.champRaceId());
            jdbc.update("UPDATE sport_formats SET display_name='default' WHERE id=(SELECT sport_format_id FROM races WHERE id=?)",
                    seed.champRaceId());

            assertMigrationFails(dataSource, "duplicate case-insensitive Race names");
        }
    }

    @Test
    void rejectsProjectedRaceNameLongerThanColumn() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            migrateToV24(dataSource);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Seed seed = seedRepresentativeData(jdbc);
            jdbc.update("UPDATE sport_formats SET display_name=? WHERE id=(SELECT sport_format_id FROM races WHERE id=?)",
                    "Ф".repeat(200), seed.champRaceId());
            jdbc.update("UPDATE races SET name=? WHERE id=?", "Д".repeat(100), seed.champRaceId());

            assertMigrationFails(dataSource, "longer than 255 characters");
        }
    }

    @Test
    void rejectsRaceWhoseFormatBelongsToAnotherEvent() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            migrateToV24(dataSource);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Seed seed = seedRepresentativeData(jdbc);
            Long otherEventId = insertEvent(jdbc, "Other event", "other-event");
            Long otherFormatId = insertFormat(jdbc, otherEventId, "other", "Other", 0, true);
            jdbc.execute("ALTER TABLE races DROP CONSTRAINT fk_races_sport_format_event");
            jdbc.update("UPDATE races SET sport_format_id=? WHERE id=?", otherFormatId, seed.defaultRaceId());

            assertMigrationFails(dataSource, "no valid SportFormat in the same Event");
        }
    }

    private static void migrateToV24(DataSource dataSource) {
        Flyway.configure().dataSource(dataSource).target("24").load().migrate();
    }

    private static void assertMigrationFails(DataSource dataSource, String message) {
        assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource).target("25").load().migrate())
                .hasStackTraceContaining(message);
    }

    private static Seed seedRepresentativeData(JdbcTemplate jdbc) {
        Long eventId = insertEvent(jdbc, "V25 Event", "v25-event");
        Long defaultFormatId = insertFormat(jdbc, eventId, "default", "Основной формат", 2, true);
        Long massFormatId = insertFormat(jdbc, eventId, "mass", " Масс-старт ", 0, false);
        Long champFormatId = insertFormat(jdbc, eventId, "champ", "Чемпионат", 1, true);

        Long defaultRaceId = insertRace(jdbc, eventId, defaultFormatId, "10 km", " 10 км ", "10-km", 9, true);
        Long massRaceId = insertRace(jdbc, eventId, massFormatId, "mass-10", "10 км", "mass-10", 5, true);
        Long massPrefixedRaceId = insertRace(
                jdbc, eventId, massFormatId, "mass-20", "  Масс-старт   20 км  ", "mass-20", 1, true);
        Long champRaceId = insertRace(
                jdbc, eventId, champFormatId, "champ", "Чемпионат", "champ", 0, true);

        Long categoryId = jdbc.queryForObject("""
                INSERT INTO categories(race_id, source_name, display_name, display_order, created_at, updated_at)
                VALUES (?, 'OPEN', 'Open', 0, now(), now()) RETURNING id
                """, Long.class, defaultRaceId);
        Long clusterId = jdbc.queryForObject("""
                INSERT INTO start_clusters(race_id, code, display_name, display_order, created_at, updated_at)
                VALUES (?, 'A', 'Cluster A', 0, now(), now()) RETURNING id
                """, Long.class, defaultRaceId);
        Long checkpointId = jdbc.queryForObject("""
                INSERT INTO checkpoints(race_id, code, name, sequence_number, created_at, updated_at)
                VALUES (?, 'CP1', 'Checkpoint 1', 1, now(), now()) RETURNING id
                """, Long.class, defaultRaceId);
        Long importBatchId = jdbc.queryForObject("""
                INSERT INTO import_batches(
                    event_id, race_id, scope_type, source_filename, file_sha256, status,
                    total_rows, imported_rows, started_at, finished_at, created_at, updated_at
                ) VALUES (?, ?, 'RACE', 'v25.csv', repeat('a', 64), 'SUCCEEDED', 1, 1,
                          now(), now(), now(), now()) RETURNING id
                """, Long.class, eventId, defaultRaceId);
        Long registrationId = jdbc.queryForObject("""
                INSERT INTO registrations(
                    race_id, category_id, cluster_id, import_batch_id, entry_kind, bib, display_name,
                    source_row_number, source_row_hash, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'PERSON', 'A-1', 'V25 Runner', 2, repeat('b', 64), now(), now())
                RETURNING id
                """, Long.class, defaultRaceId, categoryId, clusterId, importBatchId);
        Long resultId = jdbc.queryForObject("""
                INSERT INTO results(registration_id, status, gun_time_ms, chip_time_ms, created_at, updated_at)
                VALUES (?, 'finished', 1000, 900, now(), now()) RETURNING id
                """, Long.class, registrationId);
        Long splitId = jdbc.queryForObject("""
                INSERT INTO splits(result_id, checkpoint_id, gun_time_ms, created_at, updated_at)
                VALUES (?, ?, 500, now(), now()) RETURNING id
                """, Long.class, resultId, checkpointId);
        jdbc.update("""
                INSERT INTO award_policies(
                    race_id, ranking_basis, primary_standing_mode, absolute_prize_places,
                    category_enabled, category_prize_places, exclude_absolute_winners_from_category,
                    created_at, updated_at
                ) VALUES (?, 'GUN_TIME', 'ALL', 3, false, 0, false, now(), now())
                """, defaultRaceId);
        jdbc.update("""
                INSERT INTO race_result_publication_history(
                    race_id, from_status, to_status, actor, created_at
                ) VALUES (?, 'DRAFT', 'PUBLISHED', 'migration-test', now())
                """, defaultRaceId);
        UUID recalculationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO result_recalculation_operations(
                    id, event_id, status, base_revision, configuration_digest, plan_digest,
                    preview_summary, current_count, changed_count, blocking_count,
                    created_by, created_at
                ) VALUES (?, ?, 'PREVIEWED', 0, repeat('c', 64), repeat('d', 64),
                          '{}'::jsonb, 1, 0, 0, 'migration-test', now())
                """, recalculationId, eventId);
        jdbc.update("INSERT INTO result_recalculation_operation_races(operation_id, race_id) VALUES (?, ?)",
                recalculationId, defaultRaceId);
        Long issueId = jdbc.queryForObject("""
                INSERT INTO result_issue_requests(
                    event_id, registration_id, result_id, issue_type, correction_reason, status,
                    contact_email, message, observed_result_status, created_at, updated_at,
                    snapshot_origin, snapshot_event_name,
                    snapshot_sport_format_id, snapshot_sport_format_name, snapshot_sport_format_code,
                    snapshot_race_id, snapshot_race_name, snapshot_race_code,
                    snapshot_bib, snapshot_display_name
                ) VALUES (?, ?, ?, 'RESULT_CORRECTION', 'OTHER', 'RESOLVED',
                          'runner@example.test', 'Historical snapshot', 'finished', now(), now(),
                          'CAPTURED_AT_CREATION', 'V25 Event', ?, 'Основной формат', 'default',
                          ?, ' 10 км ', '10 km', 'A-1', 'V25 Runner') RETURNING id
                """, Long.class, eventId, registrationId, resultId, defaultFormatId, defaultRaceId);
        return new Seed(
                eventId, defaultFormatId, defaultRaceId, massRaceId, massPrefixedRaceId, champRaceId,
                categoryId, clusterId, checkpointId, importBatchId, registrationId, resultId, splitId, issueId);
    }

    private static Long insertEvent(JdbcTemplate jdbc, String name, String slug) {
        Long seriesId = jdbc.queryForObject("""
                INSERT INTO event_series(name, slug, active, created_at, updated_at)
                VALUES (?, ?, true, now(), now()) RETURNING id
                """, Long.class, name + " Series", slug + "-series");
        return jdbc.queryForObject("""
                INSERT INTO events(event_series_id, name, slug, created_at, updated_at)
                VALUES (?, ?, ?, now(), now()) RETURNING id
                """, Long.class, seriesId, name, slug);
    }

    private static Long insertFormat(
            JdbcTemplate jdbc, Long eventId, String code, String name, int order, boolean visible) {
        return jdbc.queryForObject("""
                INSERT INTO sport_formats(
                    event_id, code, display_name, display_order, public_visible, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, now(), now()) RETURNING id
                """, Long.class, eventId, code, name, order, visible);
    }

    private static Long insertRace(
            JdbcTemplate jdbc,
            Long eventId,
            Long formatId,
            String sourceCode,
            String name,
            String slug,
            int order,
            boolean visible
    ) {
        return jdbc.queryForObject("""
                INSERT INTO races(
                    event_id, sport_format_id, source_code, name, slug, entry_mode,
                    display_order, public_visible, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, 'UNKNOWN', ?, ?, now(), now()) RETURNING id
                """, Long.class, eventId, formatId, sourceCode, name, slug, order, visible);
    }

    private static boolean tableExists(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("""
                SELECT count(*) > 0 FROM information_schema.tables
                WHERE table_schema='public' AND table_name=?
                """, Boolean.class, table);
    }

    private static boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        return jdbc.queryForObject("""
                SELECT count(*) > 0 FROM information_schema.columns
                WHERE table_schema='public' AND table_name=? AND column_name=?
                """, Boolean.class, table, column);
    }

    private record Seed(
            Long eventId,
            Long defaultFormatId,
            Long defaultRaceId,
            Long massRaceId,
            Long massPrefixedRaceId,
            Long champRaceId,
            Long categoryId,
            Long clusterId,
            Long checkpointId,
            Long importBatchId,
            Long registrationId,
            Long resultId,
            Long splitId,
            Long issueId
    ) {
    }
}
