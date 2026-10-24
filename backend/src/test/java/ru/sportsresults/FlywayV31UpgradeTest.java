package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayV31UpgradeTest {

    @Test
    void upgradesV30WithoutChangingBusinessRowsAndEnforcesUserAccessConstraints() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("30").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('Series', 'series-v31', true, now(), now()) RETURNING id
                    """, Long.class);
            Long templateId = jdbc.queryForObject("""
                    INSERT INTO event_series_start_templates(
                        event_series_id, name, display_order, public_visible, created_at, updated_at
                    ) VALUES (?, '10 km', 0, true, now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long eventId = jdbc.queryForObject("""
                    INSERT INTO events(event_series_id, name, slug, created_at, updated_at)
                    VALUES (?, 'Event', 'event-v31', now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long raceId = jdbc.queryForObject("""
                    INSERT INTO races(event_id, source_code, name, slug, created_at, updated_at)
                    VALUES (?, '10K', '10 km', '10-km', now(), now()) RETURNING id
                    """, Long.class, eventId);
            Long batchId = jdbc.queryForObject("""
                    INSERT INTO import_batches(
                        event_id, race_id, scope_type, source_filename, file_sha256, status,
                        started_at, finished_at, created_at, updated_at
                    ) VALUES (?, ?, 'RACE', 'v31.csv', ?, 'SUCCEEDED', now(), now(), now(), now()) RETURNING id
                    """, Long.class, eventId, raceId, "a".repeat(64));
            Long registrationId = jdbc.queryForObject("""
                    INSERT INTO registrations(
                        race_id, import_batch_id, entry_kind, bib, display_name,
                        source_row_number, source_row_hash, created_at, updated_at
                    ) VALUES (?, ?, 'PERSON', 'V31-1', 'Synthetic Runner', 1, ?, now(), now()) RETURNING id
                    """, Long.class, raceId, batchId, "b".repeat(64));
            Long resultId = jdbc.queryForObject("""
                    INSERT INTO results(registration_id, status, gun_time_ms, chip_time_ms, created_at, updated_at)
                    VALUES (?, 'finished', 3600000, 3590000, now(), now()) RETURNING id
                    """, Long.class, registrationId);
            Long documentId = jdbc.queryForObject("""
                    INSERT INTO event_documents(
                        event_id, type, display_name, original_filename, storage_key, content_type,
                        size_bytes, sha256, display_order, is_public, uploaded_at, uploaded_by,
                        created_at, updated_at
                    ) VALUES (
                        ?, 'PARTICIPANT_GUIDE', 'Guide', 'guide.pdf',
                        '550e8400-e29b-41d4-a716-446655440031.pdf', 'application/pdf', 123, ?,
                        0, true, now(), 'tester', now(), now()
                    ) RETURNING id
                    """, Long.class, eventId, "c".repeat(64));

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("31").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("31");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE id=?", Long.class, eventId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM races WHERE id=?", Long.class, raceId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM registrations WHERE id=?", Long.class, registrationId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM results WHERE id=?", Long.class, resultId)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM event_series_start_templates WHERE id=?", Long.class, templateId
            )).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM event_documents WHERE id=?", Long.class, documentId
            )).isEqualTo(1);

            Long userId = jdbc.queryForObject("""
                    INSERT INTO admin_users(
                        username, password_hash, display_name, role, active, created_at, updated_at
                    ) VALUES ('Timer', '$2a$10$012345678901234567890u01234567890123456789012345678901',
                              'Timer', 'TIMEKEEPER', true, now(), now()) RETURNING id
                    """, Long.class);
            jdbc.update("""
                    INSERT INTO event_user_access(user_id, event_id, created_at, updated_at)
                    VALUES (?, ?, now(), now())
                    """, userId, eventId);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO admin_users(
                        username, password_hash, display_name, role, active, created_at, updated_at
                    ) VALUES ('timer', 'hash', 'Duplicate', 'TIMEKEEPER', true, now(), now())
                    """))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO event_user_access(user_id, event_id, created_at, updated_at)
                    VALUES (?, ?, now(), now())
                    """, userId, eventId))
                    .isInstanceOf(DataIntegrityViolationException.class);

            jdbc.update("DELETE FROM admin_users WHERE id=?", userId);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM event_user_access WHERE user_id=?", Long.class, userId
            )).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM events WHERE id=?", Long.class, eventId)).isEqualTo(1);
        }
    }

    @Test
    void migratesAndValidatesAFreshDatabase() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            Flyway flyway = Flyway.configure().dataSource(postgres.getPostgresDatabase()).load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("32");
        }
    }
}
