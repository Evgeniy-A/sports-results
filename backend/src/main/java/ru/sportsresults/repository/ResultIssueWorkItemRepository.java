package ru.sportsresults.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.ResultIssueWorkItem;

import java.util.Optional;

public interface ResultIssueWorkItemRepository extends JpaRepository<ResultIssueWorkItem, Long> {

    @EntityGraph(attributePaths = {"assignee", "result"})
    Optional<ResultIssueWorkItem> findByIssueRequest_Id(Long issueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "issueRequest", "issueRequest.event", "issueRequest.registration",
            "issueRequest.registration.race", "baseRace", "desiredRace", "result", "assignee"
    })
    @Query("select item from ResultIssueWorkItem item where item.issueRequest.id = :issueId")
    Optional<ResultIssueWorkItem> findByIssueIdForUpdate(@Param("issueId") Long issueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "issueRequest", "issueRequest.event", "issueRequest.registration",
            "issueRequest.registration.race", "baseRace", "desiredRace", "result", "assignee"
    })
    @Query("select item from ResultIssueWorkItem item where item.id = :itemId")
    Optional<ResultIssueWorkItem> findByIdForUpdate(@Param("itemId") Long itemId);

    @Query("select item.issueRequest.event.id from ResultIssueWorkItem item where item.id = :itemId")
    Optional<Long> findEventIdById(@Param("itemId") Long itemId);
}
