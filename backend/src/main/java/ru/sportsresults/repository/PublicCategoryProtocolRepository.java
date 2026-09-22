package ru.sportsresults.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PublicCategoryProtocolRepository {

    private static final String PRIMARY_PRIZE_WINNERS = """
            WITH eligible AS (
                SELECT result.id AS result_id,
                       registration.gender,
                       registration.category_id,
                       policy.primary_standing_mode,
                       policy.absolute_prize_places,
                       CASE policy.ranking_basis
                           WHEN 'CHIP_TIME' THEN result.chip_time_ms
                           WHEN 'GUN_TIME' THEN result.gun_time_ms
                           ELSE NULL
                       END AS ranking_time_ms
                FROM results result
                JOIN registrations registration ON registration.id = result.registration_id
                JOIN races race ON race.id = registration.race_id
                JOIN award_policies policy ON policy.race_id = race.id
                WHERE race.event_id = :eventId
                  AND race.id = :raceId
                  AND registration.retired_at IS NULL
                  AND policy.ranking_basis <> 'NONE'
                  AND policy.category_enabled
                  AND policy.exclude_absolute_winners_from_category
                  AND policy.absolute_prize_places > 0
                  AND lower(result.status) = 'finished'
                  AND CASE policy.ranking_basis
                          WHEN 'CHIP_TIME' THEN result.chip_time_ms
                          WHEN 'GUN_TIME' THEN result.gun_time_ms
                          ELSE NULL
                      END IS NOT NULL
            ), primary_ranked AS (
                SELECT eligible.*,
                       CASE primary_standing_mode
                           WHEN 'ALL' THEN rank() OVER (ORDER BY ranking_time_ms)
                           WHEN 'BY_GENDER' THEN CASE WHEN gender IS NOT NULL THEN
                               rank() OVER (PARTITION BY lower(gender) ORDER BY ranking_time_ms)
                           END
                           ELSE NULL
                       END AS primary_place
                FROM eligible
            )
            SELECT result_id
            FROM primary_ranked
            WHERE category_id = :categoryId
              AND primary_place IS NOT NULL
              AND primary_place <= absolute_prize_places
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PublicCategoryProtocolRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Long> findExcludedPrimaryPrizeWinnerIds(Long eventId, Long raceId, Long categoryId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("raceId", raceId)
                .addValue("categoryId", categoryId);
        return jdbc.queryForList(PRIMARY_PRIZE_WINNERS, parameters, Long.class);
    }
}
