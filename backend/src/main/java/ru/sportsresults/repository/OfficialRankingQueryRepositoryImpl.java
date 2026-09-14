package ru.sportsresults.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.sportsresults.domain.RankingAchievementType;

import java.util.Collection;
import java.util.List;

@Repository
public class OfficialRankingQueryRepositoryImpl implements OfficialRankingQueryRepository {

    private static final String ACHIEVEMENT_SQL = """
            WITH eligible AS (
                SELECT res.id AS result_id,
                       race.id AS race_id,
                       reg.gender,
                       category.id AS category_id,
                       category.source_name AS category_source_name,
                       category.display_name AS category_display_name,
                       policy.primary_standing_mode,
                       policy.absolute_prize_places,
                       policy.category_enabled,
                       policy.category_prize_places,
                       policy.exclude_absolute_winners_from_category,
                       CASE policy.ranking_basis
                            WHEN 'CHIP_TIME' THEN res.chip_time_ms
                            WHEN 'GUN_TIME' THEN res.gun_time_ms
                            ELSE NULL END AS ranking_time_ms
                FROM results res
                JOIN registrations reg ON reg.id = res.registration_id
                JOIN races race ON race.id = reg.race_id
                JOIN award_policies policy ON policy.race_id = race.id
                LEFT JOIN categories category ON category.id = reg.category_id AND category.enabled
                WHERE race.event_id = :eventId
                  AND race.id IN (:raceIds)
                  AND reg.retired_at IS NULL
                  AND policy.ranking_basis <> 'NONE'
                  AND lower(res.status) = 'finished'
                  AND CASE policy.ranking_basis
                           WHEN 'CHIP_TIME' THEN res.chip_time_ms
                           WHEN 'GUN_TIME' THEN res.gun_time_ms
                           ELSE NULL END IS NOT NULL
            ), primary_ranked AS (
                SELECT eligible.*,
                       CASE primary_standing_mode
                           WHEN 'ALL' THEN rank() OVER (
                               PARTITION BY race_id ORDER BY ranking_time_ms
                           )
                           WHEN 'BY_GENDER' THEN CASE WHEN gender IS NOT NULL THEN
                               rank() OVER (
                                   PARTITION BY race_id, lower(gender) ORDER BY ranking_time_ms
                               )
                           END
                           ELSE NULL
                       END AS primary_place
                FROM eligible
            ), category_eligible AS (
                SELECT *
                FROM primary_ranked
                WHERE category_enabled
                  AND category_id IS NOT NULL
                  AND NOT (
                      exclude_absolute_winners_from_category
                      AND primary_place IS NOT NULL
                      AND primary_place <= absolute_prize_places
                  )
            ), category_ranked AS (
                SELECT category_eligible.*,
                       rank() OVER (
                           PARTITION BY race_id, category_id ORDER BY ranking_time_ms
                       ) AS category_place
                FROM category_eligible
            ), achievements AS (
                SELECT result_id,
                       primary_place AS place,
                       CASE WHEN primary_standing_mode = 'ALL' THEN 'ABSOLUTE' ELSE 'GENDER' END AS achievement_type,
                       NULL::bigint AS category_id,
                       NULL::varchar AS category_source_name,
                       NULL::varchar AS category_display_name,
                       gender,
                       absolute_prize_places > 0
                           AND primary_place <= absolute_prize_places AS prize
                FROM primary_ranked
                WHERE primary_place IS NOT NULL
                UNION ALL
                SELECT result_id,
                       category_place AS place,
                       'CATEGORY' AS achievement_type,
                       category_id,
                       category_source_name,
                       category_display_name,
                       NULL::varchar AS gender,
                       category_prize_places > 0
                           AND category_place <= category_prize_places AS prize
                FROM category_ranked
            )
            SELECT result_id, place, achievement_type, category_id,
                   category_source_name, category_display_name, gender, prize
            FROM achievements
            WHERE result_id IN (:resultIds)
            ORDER BY result_id,
                     CASE achievement_type WHEN 'ABSOLUTE' THEN 0 WHEN 'GENDER' THEN 1 ELSE 2 END,
                     place
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public OfficialRankingQueryRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<OfficialRankingProjection> findAchievements(
            Long eventId,
            Collection<Long> raceIds,
            Collection<Long> resultIds
    ) {
        if (raceIds.isEmpty() || resultIds.isEmpty()) return List.of();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("raceIds", raceIds)
                .addValue("resultIds", resultIds);
        return jdbc.query(ACHIEVEMENT_SQL, parameters, (resultSet, rowNumber) -> new OfficialRankingProjection(
                resultSet.getLong("result_id"),
                resultSet.getInt("place"),
                RankingAchievementType.valueOf(resultSet.getString("achievement_type")),
                nullableLong(resultSet, "category_id"),
                resultSet.getString("category_source_name"),
                resultSet.getString("category_display_name"),
                resultSet.getString("gender"),
                resultSet.getBoolean("prize")
        ));
    }

    private static Long nullableLong(java.sql.ResultSet resultSet, String column) throws java.sql.SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
