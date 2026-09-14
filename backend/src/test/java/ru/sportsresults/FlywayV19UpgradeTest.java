package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayV19UpgradeTest {

    @Test
    void backfillsRacePublicationFromEventAndDefaultsNewRacesToDraft() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("18").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('V19 series', 'v19-series', TRUE, now(), now()) RETURNING id
                    """, Long.class);
            Long publishedEventId = event(jdbc, seriesId, "published", "PUBLISHED");
            Long draftEventId = event(jdbc, seriesId, "draft", "DRAFT");
            Long publishedFormatId = format(jdbc, publishedEventId, "published");
            Long draftFormatId = format(jdbc, draftEventId, "draft");
            Long publishedRaceA = race(jdbc, publishedEventId, publishedFormatId, "A");
            Long publishedRaceB = race(jdbc, publishedEventId, publishedFormatId, "B");
            Long draftRace = race(jdbc, draftEventId, draftFormatId, "C");

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("19").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");
            assertThat(status(jdbc, publishedRaceA)).isEqualTo("PUBLISHED");
            assertThat(status(jdbc, publishedRaceB)).isEqualTo("PUBLISHED");
            assertThat(status(jdbc, draftRace)).isEqualTo("DRAFT");

            Long newRace = jdbc.queryForObject("""
                    INSERT INTO races(
                        event_id, sport_format_id, source_code, name, slug, display_order,
                        public_ranking_basis, public_visible, created_at, updated_at
                    ) VALUES (?, ?, 'D', 'D', 'd', 3, 'CHIP_TIME', TRUE, now(), now())
                    RETURNING id
                    """, Long.class, publishedEventId, publishedFormatId);
            assertThat(status(jdbc, newRace)).isEqualTo("DRAFT");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM race_result_publication_history", Integer.class
            )).isZero();
        }
    }

    private static Long event(JdbcTemplate jdbc, Long seriesId, String slug, String resultsStatus) {
        return jdbc.queryForObject("""
                INSERT INTO events(
                    event_series_id, name, slug, time_zone, publication_status,
                    results_publication_status, result_inquiry_enabled, result_data_revision,
                    created_at, updated_at
                ) VALUES (?, ?, ?, 'Europe/Moscow', 'PUBLISHED', ?, FALSE, 4, now(), now())
                RETURNING id
                """, Long.class, seriesId, "V19 " + slug, "v19-" + slug, resultsStatus);
    }

    private static Long format(JdbcTemplate jdbc, Long eventId, String code) {
        return jdbc.queryForObject("""
                INSERT INTO sport_formats(
                    event_id, code, display_name, display_order, public_visible, created_at, updated_at
                ) VALUES (?, ?, ?, 0, TRUE, now(), now()) RETURNING id
                """, Long.class, eventId, code, code);
    }

    private static Long race(JdbcTemplate jdbc, Long eventId, Long formatId, String code) {
        return jdbc.queryForObject("""
                INSERT INTO races(
                    event_id, sport_format_id, source_code, name, slug, display_order,
                    public_ranking_basis, public_visible, created_at, updated_at
                ) VALUES (?, ?, ?, ?, lower(?), 0, 'CHIP_TIME', TRUE, now(), now()) RETURNING id
                """, Long.class, eventId, formatId, code, code, code);
    }

    private static String status(JdbcTemplate jdbc, Long raceId) {
        return jdbc.queryForObject(
                "SELECT results_publication_status FROM races WHERE id=?", String.class, raceId
        );
    }
}
