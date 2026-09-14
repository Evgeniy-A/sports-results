package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayV7UpgradeTest {

    @Test
    void upgradesLegacyDataAndBackfillsOneSportFormatPerEventWithoutChangingRaces() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("7").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('Legacy', 'legacy', TRUE, now(), now())
                    """);
            Long seriesId = jdbc.queryForObject("SELECT id FROM event_series WHERE slug='legacy'", Long.class);
            jdbc.update("""
                    INSERT INTO events(event_series_id, name, slug, starts_at, publication_status, created_at, updated_at)
                    VALUES (?, 'Published legacy event', 'legacy-published', now(), 'PUBLISHED', now(), now()),
                           (?, 'Draft legacy event', 'legacy-draft', now(), 'DRAFT', now(), now())
                    """, seriesId, seriesId);
            Long publishedEventId = jdbc.queryForObject(
                    "SELECT id FROM events WHERE slug='legacy-published'", Long.class);
            jdbc.update("""
                    INSERT INTO races(event_id, source_code, name, slug, display_order, created_at, updated_at)
                    VALUES (?, '10 km', '10 km', '10-km', 0, now(), now())
                    """, publishedEventId);
            Long legacyRaceId = jdbc.queryForObject(
                    "SELECT id FROM races WHERE event_id=? AND source_code='10 km'", Long.class, publishedEventId);

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(jdbc.queryForObject(
                    "SELECT results_publication_status FROM events WHERE slug='legacy-published'", String.class))
                    .isEqualTo("PUBLISHED");
            assertThat(jdbc.queryForObject(
                    "SELECT results_publication_status FROM events WHERE slug='legacy-draft'", String.class))
                    .isEqualTo("DRAFT");
            assertThat(jdbc.queryForObject(
                    "SELECT time_zone FROM events WHERE slug='legacy-published'", String.class))
                    .isEqualTo("Europe/Moscow");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM start_clusters", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sport_formats", Long.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT bool_and(public_visible) FROM sport_formats", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT bool_and(public_visible) FROM races", Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                    FROM races race
                    JOIN sport_formats format ON format.id = race.sport_format_id
                    WHERE race.id = ? AND format.event_id = race.event_id AND format.code = 'default'
                    """, Long.class, legacyRaceId)).isOne();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM races WHERE id=?", Long.class, legacyRaceId)).isOne();
            jdbc.update("UPDATE races SET public_ranking_basis='NONE' WHERE id=?", legacyRaceId);
            jdbc.update("""
                    INSERT INTO award_policies(
                        race_id, ranking_basis, primary_standing_mode, absolute_prize_places,
                        category_enabled, age_calculation_mode, category_prize_places,
                        exclude_absolute_winners_from_category, created_at, updated_at
                    ) VALUES (?, 'NONE', 'NONE', 0, FALSE, 'EVENT_DATE', 0, FALSE, now(), now())
                    """, legacyRaceId);
            assertThat(jdbc.queryForObject(
                    "SELECT ranking_basis FROM award_policies WHERE race_id=?", String.class, legacyRaceId))
                    .isEqualTo("NONE");
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE award_policies SET absolute_prize_places=1 WHERE race_id=?", legacyRaceId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }
}
