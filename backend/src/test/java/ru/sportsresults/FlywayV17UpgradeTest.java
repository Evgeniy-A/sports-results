package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV17UpgradeTest {

    @Test
    void addsApplyMetadataAndItemAuditWithoutChangingSportsFacts() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("16").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('V17 series', 'v17-series', TRUE, now(), now()) RETURNING id
                    """, Long.class);
            Long eventId = jdbc.queryForObject("""
                    INSERT INTO events(
                        event_series_id, name, slug, time_zone, publication_status,
                        results_publication_status, result_inquiry_enabled, result_data_revision,
                        created_at, updated_at
                    ) VALUES (?, 'V17 event', 'v17-event', 'Europe/Moscow', 'PUBLISHED',
                              'PUBLISHED', FALSE, 4, now(), now()) RETURNING id
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
            UUID operationId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO import_operations(
                        id, event_id, operation_mode, status, source_filename, file_sha256,
                        base_revision, plan_digest, created_by, preview_summary,
                        created_at, updated_at, expires_at
                    ) VALUES (?, ?, 'ADD_NEW', 'PREVIEWED', 'preview.csv', repeat('a', 64),
                              4, repeat('b', 64), 'admin', '{}'::jsonb,
                              now(), now(), now() + interval '1 day')
                    """, operationId, eventId);
            jdbc.update("INSERT INTO import_operation_races(operation_id, race_id) VALUES (?, ?)",
                    operationId, raceId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("17").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("17");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema='public' AND table_name='import_operations'
                      AND column_name IN (
                        'import_batch_id', 'applied_by', 'applied_at', 'inserted_count',
                        'existing_skipped_count', 'out_of_scope_count', 'new_revision'
                      )
                    """, Integer.class)).isEqualTo(7);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema='public' AND table_name='import_operation_items'
                    """, Integer.class)).isOne();
            assertThat(jdbc.queryForObject(
                    "SELECT result_data_revision FROM events WHERE id=?", Long.class, eventId)).isEqualTo(4);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM import_operations WHERE id=? AND status='PREVIEWED'", Long.class,
                    operationId)).isOne();
        }
    }
}
