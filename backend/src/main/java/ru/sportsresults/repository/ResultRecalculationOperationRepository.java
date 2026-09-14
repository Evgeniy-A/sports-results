package ru.sportsresults.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.ResultRecalculationOperation;

import java.util.Optional;
import java.util.UUID;

public interface ResultRecalculationOperationRepository
        extends JpaRepository<ResultRecalculationOperation, UUID> {

    @EntityGraph(attributePaths = {"event", "races"})
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select operation from ResultRecalculationOperation operation "
            + "where operation.id = :operationId and operation.event.id = :eventId")
    Optional<ResultRecalculationOperation> findByIdAndEventIdForUpdate(
            @Param("operationId") UUID operationId,
            @Param("eventId") Long eventId
    );
}
