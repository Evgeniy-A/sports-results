package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import ru.sportsresults.domain.ImportOperation;

import java.util.UUID;
import java.util.Optional;

public interface ImportOperationRepository extends JpaRepository<ImportOperation, UUID> {
    @EntityGraph(attributePaths = {"event", "races"})
    @Query("select operation from ImportOperation operation where operation.id = :operationId")
    Optional<ImportOperation> findWithEventById(@Param("operationId") UUID operationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select operation from ImportOperation operation where operation.id = :operationId")
    Optional<ImportOperation> findByIdForUpdate(@Param("operationId") UUID operationId);
}
