package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.ResultIssueShareBatch;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface ResultIssueShareBatchRepository extends JpaRepository<ResultIssueShareBatch, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select batch from ResultIssueShareBatch batch where batch.id = :batchId")
    Optional<ResultIssueShareBatch> findByIdForUpdate(@Param("batchId") UUID batchId);
}
