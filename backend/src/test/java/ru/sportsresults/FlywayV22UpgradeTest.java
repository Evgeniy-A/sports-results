package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV22UpgradeTest {

    @Test
    void upgradesV21DataWithSafeRecalculationState() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("21").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Seed seed = seed(jdbc);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("22").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("22");
            assertThat(jdbc.queryForObject(
                    "SELECT result_recalculation_required FROM races WHERE id=?",
                    Boolean.class,
                    seed.raceId()
            )).isFalse();

            UUID operationId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO result_recalculation_operations(
                        id, event_id, status, base_revision, configuration_digest, plan_digest,
                        preview_summary, current_count, changed_count, blocking_count,
                        created_by, created_at
                    ) VALUES (?, ?, 'PREVIEWED', 0, repeat('a', 64), repeat('b', 64),
                              '{}'::jsonb, 1, 1, 0, 'migration-test', now())
                    """, operationId, seed.eventId());
            jdbc.update("""
                    INSERT INTO result_recalculation_operation_races(operation_id, race_id)
                    VALUES (?, ?)
                    """, operationId, seed.raceId());

            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM result_recalculation_operation_races WHERE operation_id=?",
                    Integer.class,
                    operationId
            )).isEqualTo(1);
        }
    }

    @Test
    void migratesAndValidatesAFreshDatabase() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getPostgresDatabase())
                    .target("22")
                    .load();
            flyway.migrate();
            flyway.validate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("22");
        }
    }

    private static Seed seed(JdbcTemplate jdbc) {
        Long seriesId = jdbc.queryForObject("""
                INSERT INTO event_series(name, slug, active, created_at, updated_at)
                VALUES ('V22 series', 'v22-series', TRUE, now(), now()) RETURNING id
                """, Long.class);
        Long eventId = jdbc.queryForObject("""
                INSERT INTO events(
                    event_series_id, name, slug, starts_at, time_zone,
                    publication_status, results_publication_status, result_inquiry_enabled,
                    result_data_revision, created_at, updated_at
                ) VALUES (?, 'V22', 'v22', now(), 'UTC', 'PUBLISHED', 'PUBLISHED', FALSE, 0, now(), now())
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
        return new Seed(eventId, raceId);
    }

    private record Seed(Long eventId, Long raceId) {
    }
}
