package ru.sportsresults.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.ResultIssueJournalShareGrant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultIssueJournalShareGrantRepository
        extends JpaRepository<ResultIssueJournalShareGrant, UUID> {

    @EntityGraph(attributePaths = "eventSeries")
    Optional<ResultIssueJournalShareGrant> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grant from ResultIssueJournalShareGrant grant join fetch grant.eventSeries "
            + "where grant.tokenHash = :tokenHash")
    Optional<ResultIssueJournalShareGrant> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @EntityGraph(attributePaths = "eventSeries")
    List<ResultIssueJournalShareGrant> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grant from ResultIssueJournalShareGrant grant join fetch grant.eventSeries where grant.id = :id")
    Optional<ResultIssueJournalShareGrant> findByIdForUpdate(@Param("id") UUID id);
}
