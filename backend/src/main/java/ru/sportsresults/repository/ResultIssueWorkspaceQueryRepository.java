package ru.sportsresults.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.sportsresults.api.dto.ResultIssueWorkspaceRowDto;
import ru.sportsresults.api.dto.ResultIssueWorkspaceSheetDto;
import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;
import ru.sportsresults.domain.ResultIssueWorkSyncStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Repository
public class ResultIssueWorkspaceQueryRepository {

    private static final String EVENT_YEAR = "EXTRACT(YEAR FROM (COALESCE(e.starts_at, issue.snapshot_event_starts_at) "
            + "AT TIME ZONE COALESCE(e.time_zone, 'UTC')))::int";

    private final NamedParameterJdbcTemplate jdbc;

    public ResultIssueWorkspaceQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ResultIssueWorkspaceSheetDto> findSheets() {
        String sql = """
                SELECT series.id AS series_id,
                       series.name AS series_name,
                       %s AS event_year,
                       COUNT(*) AS issue_count,
                       COUNT(*) FILTER (WHERE work.sync_status = 'PENDING') AS pending_sync_count,
                       COUNT(*) FILTER (WHERE issue.status = 'NEW') AS new_count,
                       COUNT(*) FILTER (WHERE issue.status = 'IN_PROGRESS') AS in_progress_count,
                       MAX(work.synced_at) AS last_synced_at
                FROM result_issue_requests issue
                JOIN events e ON e.id = issue.event_id
                JOIN event_series series ON series.id = e.event_series_id
                LEFT JOIN result_issue_work_items work ON work.issue_request_id = issue.id
                WHERE COALESCE(e.starts_at, issue.snapshot_event_starts_at) IS NOT NULL
                GROUP BY series.id, series.name, %s
                ORDER BY event_year DESC, lower(series.name), series.id
                """.formatted(EVENT_YEAR, EVENT_YEAR);
        return jdbc.query(sql, new MapSqlParameterSource(), (rs, rowNum) -> new ResultIssueWorkspaceSheetDto(
                rs.getLong("series_id"),
                rs.getString("series_name"),
                rs.getInt("event_year"),
                rs.getLong("issue_count"),
                rs.getLong("pending_sync_count"),
                rs.getLong("new_count"),
                rs.getLong("in_progress_count"),
                instant(rs, "last_synced_at")
        ));
    }

    public List<ResultIssueWorkspaceRowDto> findRows(long seriesId, int year) {
        String sql = """
                SELECT issue.id AS issue_id,
                       series.id AS series_id,
                       series.name AS series_name,
                       e.id AS event_id,
                       e.name AS event_name,
                       %s AS event_year,
                       COALESCE(e.location, issue.snapshot_event_location) AS location,
                       race.id AS race_id,
                       race.name AS race_name,
                       registration.id AS registration_id,
                       registration.retired_at IS NULL AS registration_current,
                       COALESCE(registration.bib, issue.snapshot_bib) AS bib,
                       COALESCE(registration.display_name, issue.snapshot_display_name) AS participant_name,
                       COALESCE(category.display_name, issue.snapshot_effective_category_name) AS category_name,
                       issue.issue_type,
                       issue.correction_reason,
                       issue.status AS issue_status,
                       (SELECT COUNT(*) FROM result_issue_attachments attachment
                         WHERE attachment.issue_request_id = issue.id) AS attachment_count,
                       result.id AS current_result_id,
                       result.status AS current_result_status,
                       result.gun_time_ms AS current_gun_time_ms,
                       result.chip_time_ms AS current_chip_time_ms,
                       issue.claimed_gun_time_ms,
                       issue.claimed_chip_time_ms,
                       work.id AS work_item_id,
                       work.desired_result_status,
                       work.desired_gun_time_ms,
                       work.desired_chip_time_ms,
                       assignee.id AS assignee_user_id,
                       assignee.display_name AS assignee_name,
                       work.internal_comment,
                       work.sync_status,
                       work.sync_error_code,
                       work.version,
                       work.updated_at AS work_updated_at,
                       work.updated_by,
                       work.synced_at,
                       work.synced_by,
                       GREATEST(issue.updated_at, COALESCE(work.updated_at, issue.updated_at)) AS row_updated_at
                FROM result_issue_requests issue
                JOIN events e ON e.id = issue.event_id
                JOIN event_series series ON series.id = e.event_series_id
                JOIN registrations registration ON registration.id = issue.registration_id
                JOIN races race ON race.id = registration.race_id
                LEFT JOIN categories category ON category.id = registration.category_id
                LEFT JOIN results result ON result.registration_id = registration.id
                LEFT JOIN result_issue_work_items work ON work.issue_request_id = issue.id
                LEFT JOIN admin_users assignee ON assignee.id = work.assignee_user_id
                WHERE series.id = :seriesId
                  AND %s = :year
                ORDER BY lower(COALESCE(e.location, issue.snapshot_event_location, '')),
                         e.starts_at NULLS LAST,
                         issue.id
                """.formatted(EVENT_YEAR, EVENT_YEAR);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("seriesId", seriesId)
                .addValue("year", year);
        return jdbc.query(sql, parameters, (rs, rowNum) -> mapRow(rs));
    }

    public List<Long> findPendingWorkItemIds(long seriesId, int year, int limit) {
        String sql = """
                SELECT work.id
                FROM result_issue_work_items work
                JOIN result_issue_requests issue ON issue.id = work.issue_request_id
                JOIN events e ON e.id = issue.event_id
                WHERE e.event_series_id = :seriesId
                  AND %s = :year
                  AND work.sync_status = 'PENDING'
                ORDER BY work.updated_at, work.id
                LIMIT :limit
                """.formatted(EVENT_YEAR);
        return jdbc.queryForList(sql, new MapSqlParameterSource()
                .addValue("seriesId", seriesId)
                .addValue("year", year)
                .addValue("limit", limit), Long.class);
    }

    public int countPending(long seriesId, int year) {
        String sql = """
                SELECT COUNT(*)
                FROM result_issue_work_items work
                JOIN result_issue_requests issue ON issue.id = work.issue_request_id
                JOIN events e ON e.id = issue.event_id
                WHERE e.event_series_id = :seriesId
                  AND %s = :year
                  AND work.sync_status = 'PENDING'
                """.formatted(EVENT_YEAR);
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("seriesId", seriesId)
                .addValue("year", year), Long.class);
        return count == null ? 0 : Math.toIntExact(count);
    }

    private static ResultIssueWorkspaceRowDto mapRow(ResultSet rs) throws SQLException {
        Long workItemId = nullableLong(rs, "work_item_id");
        ResultIssueWorkspaceRowDto.WorkItem workItem = workItemId == null ? null
                : new ResultIssueWorkspaceRowDto.WorkItem(
                        workItemId,
                        rs.getString("desired_result_status"),
                        nullableLong(rs, "desired_gun_time_ms"),
                        nullableLong(rs, "desired_chip_time_ms"),
                        nullableLong(rs, "assignee_user_id"),
                        rs.getString("assignee_name"),
                        rs.getString("internal_comment"),
                        ResultIssueWorkSyncStatus.valueOf(rs.getString("sync_status")),
                        rs.getString("sync_error_code"),
                        rs.getLong("version"),
                        instant(rs, "work_updated_at"),
                        rs.getString("updated_by"),
                        instant(rs, "synced_at"),
                        rs.getString("synced_by")
                );
        return new ResultIssueWorkspaceRowDto(
                rs.getLong("issue_id"),
                rs.getLong("series_id"),
                rs.getString("series_name"),
                rs.getLong("event_id"),
                rs.getString("event_name"),
                rs.getInt("event_year"),
                rs.getString("location"),
                rs.getLong("race_id"),
                rs.getString("race_name"),
                rs.getLong("registration_id"),
                rs.getBoolean("registration_current"),
                rs.getString("bib"),
                rs.getString("participant_name"),
                rs.getString("category_name"),
                ResultIssueType.valueOf(rs.getString("issue_type")),
                nullableEnum(rs, "correction_reason", ResultCorrectionReason.class),
                ResultIssueStatus.valueOf(rs.getString("issue_status")),
                rs.getLong("attachment_count"),
                nullableLong(rs, "current_result_id"),
                rs.getString("current_result_status"),
                nullableLong(rs, "current_gun_time_ms"),
                nullableLong(rs, "current_chip_time_ms"),
                nullableLong(rs, "claimed_gun_time_ms"),
                nullableLong(rs, "claimed_chip_time_ms"),
                workItem,
                instant(rs, "row_updated_at")
        );
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static <E extends Enum<E>> E nullableEnum(ResultSet rs, String column, Class<E> type)
            throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }
}
