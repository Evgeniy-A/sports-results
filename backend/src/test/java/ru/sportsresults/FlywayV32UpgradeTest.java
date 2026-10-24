package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV32UpgradeTest {
    @Test
    void upgradesV31WithoutChangingExistingSportsOrDocumentRows() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            var dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("31").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('Series', 'series-v32', true, now(), now()) RETURNING id
                    """, Long.class);
            Long eventId = jdbc.queryForObject("""
                    INSERT INTO events(event_series_id, name, slug, created_at, updated_at)
                    VALUES (?, 'Existing Event', 'existing-v32', now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long raceId = jdbc.queryForObject("""
                    INSERT INTO races(event_id, source_code, name, slug, created_at, updated_at)
                    VALUES (?, '10K', '10 km', '10-km', now(), now()) RETURNING id
                    """, Long.class, eventId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).load();
            flyway.migrate(); flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("32");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE id=?", Long.class, eventId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM races WHERE id=?", Long.class, raceId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM event_certificate_settings", Long.class)).isZero();
            jdbc.update("""
                    INSERT INTO event_certificate_settings(event_id, enabled, created_at, updated_at)
                    VALUES (?, true, now(), now())
                    """, eventId);
            assertThat(jdbc.queryForObject("SELECT enabled FROM event_certificate_settings WHERE event_id=?", Boolean.class, eventId)).isTrue();
        }
    }

    @Test
    void migratesAndValidatesFreshDatabase() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            Flyway flyway = Flyway.configure().dataSource(postgres.getPostgresDatabase()).load();
            flyway.migrate(); flyway.validate();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("32");
        }
    }
}
