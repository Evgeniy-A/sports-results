package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayV14UpgradeTest {

    @Test
    void upgradesV13ToOneActiveIssuePerRegistrationWithoutChangingFactsOrClosedHistory() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("13").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture fixture = seedFixture(jdbc);

            insertIssue(jdbc, fixture, fixture.firstRegistrationId(), "MISSING_RESULT", "RESOLVED");
            insertIssue(jdbc, fixture, fixture.firstRegistrationId(), "RESULT_CORRECTION", "REJECTED");
            Map<String, Object> resultBefore = jdbc.queryForMap("""
                    SELECT status, gun_time_ms, chip_time_ms, overall_place
                    FROM results WHERE id=?
                    """, fixture.firstResultId());
            Map<String, Object> settingsBefore = jdbc.queryForMap("""
                    SELECT result_inquiry_enabled, result_inquiry_window_days, result_inquiry_email
                    FROM events WHERE id=?
                    """, fixture.eventId());

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("14").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("14");
            assertThat(jdbc.queryForObject(
                    "SELECT to_regclass('public.uk_result_issue_active_registration_type')", String.class))
                    .isNull();
            String indexDefinition = jdbc.queryForObject("""
                    SELECT pg_get_indexdef('uk_result_issue_active_registration'::regclass)
                    """, String.class);
            assertThat(indexDefinition)
                    .contains("UNIQUE", "registration_id", "NEW", "IN_PROGRESS")
                    .doesNotContain("issue_type");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM result_issue_requests
                    WHERE registration_id=? AND status IN ('RESOLVED', 'REJECTED')
                    """, Long.class, fixture.firstRegistrationId())).isEqualTo(2);

            insertIssue(jdbc, fixture, fixture.firstRegistrationId(), "MISSING_RESULT", "NEW");
            assertThatThrownBy(() -> insertIssue(
                    jdbc, fixture, fixture.firstRegistrationId(), "RESULT_CORRECTION", "IN_PROGRESS"
            )).isInstanceOf(DataIntegrityViolationException.class);

            insertIssue(jdbc, fixture, fixture.secondRegistrationId(), "MISSING_RESULT", "NEW");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM result_issue_requests
                    WHERE status IN ('NEW', 'IN_PROGRESS')
                    """, Long.class)).isEqualTo(2);

            jdbc.update("""
                    UPDATE result_issue_requests
                    SET status='RESOLVED', resolved_at=now(), updated_at=now()
                    WHERE registration_id=? AND status='NEW'
                    """, fixture.firstRegistrationId());
            insertIssue(jdbc, fixture, fixture.firstRegistrationId(), "RESULT_CORRECTION", "NEW");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM result_issue_requests
                    WHERE registration_id=? AND status IN ('NEW', 'IN_PROGRESS')
                    """, Long.class, fixture.firstRegistrationId())).isOne();

            assertThat(jdbc.queryForObject("SELECT count(*) FROM registrations", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM results", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForMap("""
                    SELECT status, gun_time_ms, chip_time_ms, overall_place
                    FROM results WHERE id=?
                    """, fixture.firstResultId())).isEqualTo(resultBefore);
            assertThat(jdbc.queryForMap("""
                    SELECT result_inquiry_enabled, result_inquiry_window_days, result_inquiry_email
                    FROM events WHERE id=?
                    """, fixture.eventId())).isEqualTo(settingsBefore);
        }
    }

    @Test
    void refusesUpgradeWhenV13ContainsMultipleActiveTypesForOneRegistration() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("13").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture fixture = seedFixture(jdbc);

            insertIssue(jdbc, fixture, fixture.firstRegistrationId(), "MISSING_RESULT", "NEW");
            insertIssue(jdbc, fixture, fixture.firstRegistrationId(), "RESULT_CORRECTION", "IN_PROGRESS");

            assertThatThrownBy(() -> Flyway.configure().dataSource(dataSource).target("14").load().migrate())
                    .isInstanceOf(FlywayException.class)
                    .hasStackTraceContaining("Cannot enforce one active result issue per registration")
                    .hasStackTraceContaining(fixture.firstRegistrationId().toString());

            Flyway v13 = Flyway.configure().dataSource(dataSource).target("13").load();
            assertThat(v13.info().current().getVersion().getVersion()).isEqualTo("13");
            assertThat(jdbc.queryForObject(
                    "SELECT to_regclass('public.uk_result_issue_active_registration_type')", String.class))
                    .isNotNull();
            assertThat(jdbc.queryForObject(
                    "SELECT to_regclass('public.uk_result_issue_active_registration')", String.class))
                    .isNull();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM result_issue_requests", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM result_issue_requests
                    WHERE registration_id=? AND status IN ('NEW', 'IN_PROGRESS')
                    """, Long.class, fixture.firstRegistrationId())).isEqualTo(2);
        }
    }

    private static Fixture seedFixture(JdbcTemplate jdbc) {
        Long seriesId = jdbc.queryForObject("""
                INSERT INTO event_series(name, slug, active, created_at, updated_at)
                VALUES ('V14 series', 'v14-series', TRUE, now(), now())
                RETURNING id
                """, Long.class);
        Long eventId = jdbc.queryForObject("""
                INSERT INTO events(
                    event_series_id, name, slug, starts_at, ends_at, time_zone,
                    publication_status, results_publication_status,
                    result_inquiry_enabled, result_inquiry_window_days, result_inquiry_email,
                    created_at, updated_at
                ) VALUES (?, 'V14 event', 'v14-event', now() - interval '2 days',
                          now() - interval '1 day', 'Europe/Moscow', 'PUBLISHED', 'PUBLISHED',
                          TRUE, 76, 'timing@example.org', now(), now())
                RETURNING id
                """, Long.class, seriesId);
        Long formatId = jdbc.queryForObject("""
                INSERT INTO sport_formats(
                    event_id, code, display_name, display_order, public_visible, created_at, updated_at
                ) VALUES (?, 'individual', 'Индивидуальный', 0, TRUE, now(), now())
                RETURNING id
                """, Long.class, eventId);
        Long raceId = jdbc.queryForObject("""
                INSERT INTO races(
                    event_id, sport_format_id, source_code, name, slug, display_order,
                    public_ranking_basis, public_visible, created_at, updated_at
                ) VALUES (?, ?, '5 km', '5 км', '5-km', 0, 'GUN_TIME', TRUE, now(), now())
                RETURNING id
                """, Long.class, eventId, formatId);
        Long batchId = jdbc.queryForObject("""
                INSERT INTO import_batches(
                    event_id, scope_type, source_filename, file_sha256, status,
                    total_rows, imported_rows, skipped_rows, failed_rows,
                    started_at, finished_at, created_at, updated_at
                ) VALUES (?, 'EVENT', 'v14.csv', repeat('a', 64), 'SUCCEEDED',
                          2, 2, 0, 0, now(), now(), now(), now())
                RETURNING id
                """, Long.class, eventId);
        Long firstRegistrationId = insertRegistration(jdbc, raceId, batchId, 2, "Первый участник", "1990-01-01");
        Long secondRegistrationId = insertRegistration(jdbc, raceId, batchId, 3, "Второй участник", "1991-02-02");
        Long firstResultId = insertResult(jdbc, firstRegistrationId, 1, 1000, 900);
        insertResult(jdbc, secondRegistrationId, 2, 2000, 1900);
        return new Fixture(eventId, firstRegistrationId, secondRegistrationId, firstResultId);
    }

    private static Long insertRegistration(
            JdbcTemplate jdbc,
            Long raceId,
            Long batchId,
            int rowNumber,
            String displayName,
            String birthDate
    ) {
        return jdbc.queryForObject("""
                INSERT INTO registrations(
                    race_id, import_batch_id, entry_kind, bib, display_name, birth_date,
                    source_row_number, source_row_hash, created_at, updated_at
                ) VALUES (?, ?, 'PERSON', '1100', ?, CAST(? AS DATE), ?, repeat(?, 64), now(), now())
                RETURNING id
                """, Long.class, raceId, batchId, displayName, birthDate, rowNumber,
                rowNumber == 2 ? "b" : "c");
    }

    private static Long insertResult(
            JdbcTemplate jdbc,
            Long registrationId,
            int place,
            long gunTime,
            long chipTime
    ) {
        return jdbc.queryForObject("""
                INSERT INTO results(
                    registration_id, status, gun_time_ms, chip_time_ms, overall_place,
                    created_at, updated_at
                ) VALUES (?, 'finished', ?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, registrationId, gunTime, chipTime, place);
    }

    private static void insertIssue(
            JdbcTemplate jdbc,
            Fixture fixture,
            Long registrationId,
            String type,
            String status
    ) {
        boolean correction = type.equals("RESULT_CORRECTION");
        jdbc.update("""
                INSERT INTO result_issue_requests(
                    event_id, registration_id, result_id, issue_type, correction_reason, status,
                    contact_email, message, observed_result_status, created_at, updated_at, resolved_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'runner@example.org', 'Проверка V14', ?, now(), now(), ?)
                """,
                fixture.eventId(),
                registrationId,
                correction ? fixture.firstResultId() : null,
                type,
                correction ? "OTHER" : null,
                status,
                correction ? "finished" : null,
                status.equals("RESOLVED") || status.equals("REJECTED")
                        ? java.sql.Timestamp.from(java.time.Instant.now())
                        : null
        );
    }

    private record Fixture(
            Long eventId,
            Long firstRegistrationId,
            Long secondRegistrationId,
            Long firstResultId
    ) {
    }
}
