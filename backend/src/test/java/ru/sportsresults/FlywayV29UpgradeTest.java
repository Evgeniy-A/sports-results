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

class FlywayV29UpgradeTest {

    @Test
    void backfillsCategoryBasisAndKeepsSportsFactsAndTemplateSemantics() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure().dataSource(dataSource).target("28").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            Long seriesId = jdbc.queryForObject("""
                    INSERT INTO event_series(name, slug, active, created_at, updated_at)
                    VALUES ('Series', 'series-v29', true, now(), now()) RETURNING id
                    """, Long.class);
            Long eventId = jdbc.queryForObject("""
                    INSERT INTO events(event_series_id, name, slug, created_at, updated_at)
                    VALUES (?, 'Event', 'event-v29', now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long raceId = jdbc.queryForObject("""
                    INSERT INTO races(event_id, source_code, name, slug, display_order, created_at, updated_at)
                    VALUES (?, 'RACE', 'Race', 'race-v29', 0, now(), now()) RETURNING id
                    """, Long.class, eventId);
            Long policyId = jdbc.queryForObject("""
                    INSERT INTO award_policies(
                        race_id, ranking_basis, primary_standing_mode, absolute_prize_places,
                        category_enabled, age_calculation_mode, category_prize_places,
                        exclude_absolute_winners_from_category, created_at, updated_at
                    ) VALUES (?, 'GUN_TIME', 'ALL', 3, true, 'EVENT_DATE', 3, false, now(), now())
                    RETURNING id
                    """, Long.class, raceId);
            Long categoryId = jdbc.queryForObject("""
                    INSERT INTO categories(
                        race_id, source_name, display_name, display_order, enabled, created_at, updated_at
                    ) VALUES (?, 'A', 'A', 0, true, now(), now()) RETURNING id
                    """, Long.class, raceId);
            Long templateStartId = jdbc.queryForObject("""
                    INSERT INTO event_series_start_templates(
                        event_series_id, name, display_order, public_visible, created_at, updated_at
                    ) VALUES (?, 'Template Race', 0, true, now(), now()) RETURNING id
                    """, Long.class, seriesId);
            Long templatePolicyId = jdbc.queryForObject("""
                    INSERT INTO event_series_start_award_policy_templates(
                        template_start_id, ranking_basis, primary_standing_mode, absolute_prize_places,
                        category_enabled, age_calculation_mode, category_prize_places,
                        exclude_absolute_winners_from_category, created_at, updated_at
                    ) VALUES (?, 'CHIP_TIME', 'ALL', 3, true, 'EVENT_DATE', 3, false, now(), now())
                    RETURNING id
                    """, Long.class, templateStartId);
            Long templateCategoryId = jdbc.queryForObject("""
                    INSERT INTO event_series_start_category_templates(
                        template_start_id, source_name, display_name, display_order,
                        enabled, created_at, updated_at
                    ) VALUES (?, 'B', 'B', 0, true, now(), now()) RETURNING id
                    """, Long.class, templateStartId);
            Map<String, Object> eventBefore = jdbc.queryForMap("SELECT * FROM events WHERE id=?", eventId);

            Flyway flyway = Flyway.configure().dataSource(dataSource).target("29").load();
            flyway.migrate();
            flyway.validate();

            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("29");
            assertThat(jdbc.queryForObject(
                    "SELECT category_ranking_basis FROM award_policies WHERE id=?", String.class, policyId))
                    .isEqualTo("GUN_TIME");
            assertThat(jdbc.queryForObject(
                    "SELECT ranking_basis_override FROM categories WHERE id=?", String.class, categoryId))
                    .isNull();
            assertThat(jdbc.queryForObject(
                    "SELECT category_ranking_basis FROM event_series_start_award_policy_templates WHERE id=?",
                    String.class, templatePolicyId)).isEqualTo("CHIP_TIME");
            assertThat(jdbc.queryForObject(
                    "SELECT ranking_basis_override FROM event_series_start_category_templates WHERE id=?",
                    String.class, templateCategoryId)).isNull();
            assertThat(jdbc.queryForMap("SELECT * FROM events WHERE id=?", eventId)).isEqualTo(eventBefore);

            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE categories SET ranking_basis_override='NONE' WHERE id=?", categoryId))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE award_policies SET category_ranking_basis='NONE' WHERE id=?", policyId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
