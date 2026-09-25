package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayV28UpgradeTest {

    @Test
    void backfillsExistingEventsAndAddsIndependentTemplateDefaultsWithoutChangingSportsFacts() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("27").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('Series', 'series-v28', true, now(), now()) RETURNING id
                    """, Long.class);
            Long eventId = jdbc.queryForObject("""
                    INSERT INTO events(
                        event_series_id, name, slug, starts_at, ends_at, time_zone,
                        result_inquiry_enabled, result_inquiry_window_days, result_inquiry_email,
                        created_at, updated_at
                    ) VALUES (?, 'Event', 'event-v28', '2027-08-15T08:00:00Z', '2027-08-15T13:00:00Z',
                        'Asia/Yekaterinburg', true, 5, 'timing@example.org', now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long raceId = jdbc.queryForObject("""
                    INSERT INTO races(event_id, source_code, name, slug, display_order, created_at, updated_at)
                    VALUES (?, 'RACE', 'Race', 'race-v28', 0, now(), now()) RETURNING id
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
                    VALUES (?, ?, 'PERSON', 'SYN-28', 'Synthetic', 2, repeat('b', 64), now(), now()) RETURNING id
                    """, Long.class, raceId, batchId);
            Long resultId = jdbc.queryForObject("""
                    INSERT INTO results(registration_id, status, chip_time_ms, created_at, updated_at)
                    VALUES (?, 'finished', 1234, now(), now()) RETURNING id
                    """, Long.class, registrationId);
            Map<String, Object> registrationBefore = jdbc.queryForMap(
                    "SELECT * FROM registrations WHERE id=?", registrationId);
            Map<String, Object> resultBefore = jdbc.queryForMap("SELECT * FROM results WHERE id=?", resultId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("28").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("28");
            assertThat(jdbc.queryForObject(
                    "SELECT result_inquiry_deadline_mode FROM events WHERE id=?", String.class, eventId))
                    .isEqualTo("AFTER_EVENT_DAYS");
            assertThat(jdbc.queryForObject(
                    "SELECT result_inquiry_fixed_date FROM events WHERE id=?", java.time.LocalDate.class, eventId))
                    .isNull();
            assertThat(jdbc.queryForObject(
                    "SELECT default_result_inquiry_enabled FROM event_series WHERE id=?", Boolean.class, seriesId))
                    .isFalse();
            assertThat(jdbc.queryForObject(
                    "SELECT default_result_inquiry_deadline_mode FROM event_series WHERE id=?", String.class, seriesId))
                    .isEqualTo("AFTER_EVENT_DAYS");
            assertThat(jdbc.queryForMap("SELECT * FROM registrations WHERE id=?", registrationId))
                    .isEqualTo(registrationBefore);
            assertThat(jdbc.queryForMap("SELECT * FROM results WHERE id=?", resultId)).isEqualTo(resultBefore);

            assertThatThrownBy(() -> jdbc.update("""
                    UPDATE events SET result_inquiry_deadline_mode='FIXED_DATE', result_inquiry_fixed_date=NULL
                    WHERE id=?
                    """, eventId)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
