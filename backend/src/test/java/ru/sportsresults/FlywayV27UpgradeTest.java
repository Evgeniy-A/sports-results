package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV27UpgradeTest {

    @Test
    void addsImportProfilesAndOperationConfigWithoutChangingSportsFacts() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("26").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('Series', 'series-v27', true, now(), now()) RETURNING id
                    """, Long.class);
            Long eventId = jdbc.queryForObject("""
                    INSERT INTO events(event_series_id, name, slug, created_at, updated_at)
                    VALUES (?, 'Event', 'event-v27', now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long raceId = jdbc.queryForObject("""
                    INSERT INTO races(event_id, source_code, name, slug, display_order, created_at, updated_at)
                    VALUES (?, 'RACE', 'Race', 'race-v27', 0, now(), now()) RETURNING id
                    """, Long.class, eventId);
            Long batchId = jdbc.queryForObject("""
                    INSERT INTO import_batches(event_id, race_id, scope_type, source_filename, file_sha256,
                        status, started_at, created_at, updated_at)
                    VALUES (?, ?, 'RACE', 'before.csv', repeat('a', 64), 'SUCCEEDED', now(), now(), now())
                    RETURNING id
                    """, Long.class, eventId, raceId);
            Long registrationId = jdbc.queryForObject("""
                    INSERT INTO registrations(race_id, import_batch_id, entry_kind, bib, display_name,
                        source_row_number, source_row_hash, created_at, updated_at)
                    VALUES (?, ?, 'PERSON', 'SYN-1', 'Synthetic', 2, repeat('b', 64), now(), now()) RETURNING id
                    """, Long.class, raceId, batchId);
            Long resultId = jdbc.queryForObject("""
                    INSERT INTO results(registration_id, status, chip_time_ms, created_at, updated_at)
                    VALUES (?, 'finished', 1234, now(), now()) RETURNING id
                    """, Long.class, registrationId);
            Map<String, Object> registrationBefore = jdbc.queryForMap(
                    "SELECT * FROM registrations WHERE id=?", registrationId);
            Map<String, Object> resultBefore = jdbc.queryForMap("SELECT * FROM results WHERE id=?", resultId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("27").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("27");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema='public' AND table_name='import_mapping_profiles'
                    """, Long.class)).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema='public' AND table_name='import_operations' AND column_name='input_config'
                    """, Long.class)).isOne();
            assertThat(jdbc.queryForMap("SELECT * FROM registrations WHERE id=?", registrationId))
                    .isEqualTo(registrationBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM results WHERE id=?", resultId)).isEqualTo(resultBefore);
            jdbc.update("""
                    INSERT INTO import_mapping_profiles(name, file_type, header_signature, mapping_json,
                        created_at, updated_at)
                    VALUES ('Supplier', 'CSV', repeat('c', 64), '{"Bib":"BIB"}'::jsonb, now(), now())
                    """);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM import_mapping_profiles", Long.class)).isOne();
        }
    }
}
