package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.domain.ResultIssueStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface ResultIssueRequestRepository extends JpaRepository<ResultIssueRequest, Long>,
        ResultIssueRequestSearchRepository {
    Optional<ResultIssueRequest> findFirstByRegistration_IdAndQueueArchivedAtIsNullAndStatusInOrderByIdAsc(
            Long registrationId,
            Collection<ResultIssueStatus> statuses
    );

    boolean existsByIdAndEvent_Id(Long issueId, Long eventId);

    @EntityGraph(attributePaths = {
            "event",
            "registration",
            "registration.category",
            "registration.race",
            "registration.race.sportFormat",
            "result"
    })
    Optional<ResultIssueRequest> findByIdAndEvent_Id(Long issueId, Long eventId);

    @EntityGraph(attributePaths = {
            "event",
            "registration",
            "registration.category",
            "registration.race",
            "registration.race.sportFormat"
    })
    @Query("select issue from ResultIssueRequest issue where issue.id = :issueId")
    Optional<ResultIssueRequest> findJournalDetailById(@Param("issueId") Long issueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"event", "registration", "registration.race"})
    @Query("select issue from ResultIssueRequest issue "
            + "where issue.id = :issueId and issue.event.id = :eventId")
    Optional<ResultIssueRequest> findByIdAndEventIdForUpdate(
            @Param("issueId") Long issueId,
            @Param("eventId") Long eventId
    );

    @EntityGraph(attributePaths = {"event", "registration", "registration.race"})
    @Query("select issue from ResultIssueRequest issue "
            + "where issue.event.id = :eventId "
            + "and issue.registration.race.id in :raceIds "
            + "and issue.status in :statuses "
            + "and issue.queueArchivedAt is null "
            + "and issue.registration.retiredAt is null "
            + "order by issue.id")
    List<ResultIssueRequest> findActiveByEventAndRaceScope(
            @Param("eventId") Long eventId,
            @Param("raceIds") Collection<Long> raceIds,
            @Param("statuses") Collection<ResultIssueStatus> statuses
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE result_issue_requests
            SET status = :nextStatus,
                resolved_at = :resolvedAt,
                updated_at = :updatedAt
            WHERE id = :issueId
              AND event_id = :eventId
              AND status = :expectedStatus
            """, nativeQuery = true)
    int updateStatusIfCurrent(
            @Param("eventId") Long eventId,
            @Param("issueId") Long issueId,
            @Param("expectedStatus") String expectedStatus,
            @Param("nextStatus") String nextStatus,
            @Param("resolvedAt") Instant resolvedAt,
            @Param("updatedAt") Instant updatedAt
    );
}
