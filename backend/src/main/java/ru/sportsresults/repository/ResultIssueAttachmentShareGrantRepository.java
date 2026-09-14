package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.ResultIssueAttachmentShareGrant;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

public interface ResultIssueAttachmentShareGrantRepository
        extends JpaRepository<ResultIssueAttachmentShareGrant, UUID> {

    @EntityGraph(attributePaths = {"batch", "attachment"})
    Optional<ResultIssueAttachmentShareGrant> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select grant from ResultIssueAttachmentShareGrant grant join fetch grant.batch where grant.id = :grantId")
    Optional<ResultIssueAttachmentShareGrant> findByIdForUpdate(@Param("grantId") UUID grantId);

    long countByBatch_Id(UUID batchId);

    long countByBatch_IdAndAccessCountGreaterThan(UUID batchId, long accessCount);

    @Modifying
    @Query("""
            update ResultIssueAttachmentShareGrant grant
            set grant.lastAccessedAt = :accessedAt,
                grant.accessCount = grant.accessCount + 1
            where grant.id = :grantId
            """)
    int recordAccess(@Param("grantId") UUID grantId, @Param("accessedAt") Instant accessedAt);
}
