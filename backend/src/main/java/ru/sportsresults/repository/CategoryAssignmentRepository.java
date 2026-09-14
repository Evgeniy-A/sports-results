package ru.sportsresults.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Repository
public class CategoryAssignmentRepository {

    private static final int BATCH_SIZE = 1_000;

    private final JdbcTemplate jdbc;

    public CategoryAssignmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<RegistrationCategoryFacts> findAllByRaceId(Long raceId) {
        return jdbc.query("""
                SELECT id, birth_date, gender, source_category, category_id
                FROM registrations
                WHERE race_id = ?
                  AND retired_at IS NULL
                ORDER BY id
                """, (resultSet, rowNumber) -> new RegistrationCategoryFacts(
                resultSet.getLong("id"),
                localDate(resultSet.getDate("birth_date")),
                resultSet.getString("gender"),
                resultSet.getString("source_category"),
                resultSet.getObject("category_id", Long.class)
        ), raceId);
    }

    public void updateAll(List<CategoryAssignment> assignments) {
        if (assignments.isEmpty()) {
            return;
        }
        Instant changedAt = Instant.now();
        jdbc.batchUpdate(
                "UPDATE registrations SET category_id = ?, updated_at = ? WHERE id = ? AND retired_at IS NULL",
                assignments,
                BATCH_SIZE,
                (statement, assignment) -> {
                    if (assignment.categoryId() == null) {
                        statement.setNull(1, java.sql.Types.BIGINT);
                    } else {
                        statement.setLong(1, assignment.categoryId());
                    }
                    statement.setTimestamp(2, Timestamp.from(changedAt));
                    statement.setLong(3, assignment.registrationId());
                }
        );
    }

    public int clearCategory(Long categoryId) {
        return jdbc.update(
                "UPDATE registrations SET category_id = NULL, updated_at = ? "
                        + "WHERE category_id = ? AND retired_at IS NULL",
                Timestamp.from(Instant.now()),
                categoryId
        );
    }

    private static LocalDate localDate(Date value) {
        return value == null ? null : value.toLocalDate();
    }

    public record RegistrationCategoryFacts(
            Long registrationId,
            LocalDate birthDate,
            String gender,
            String sourceCategory,
            Long currentCategoryId
    ) {
    }

    public record CategoryAssignment(Long registrationId, Long categoryId) {
    }
}
