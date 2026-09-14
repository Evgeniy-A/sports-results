package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.ImportBatch;
import ru.sportsresults.domain.ImportBatchStatus;
import ru.sportsresults.domain.ImportScopeType;

import java.util.List;
import java.util.Optional;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    List<ImportBatch> findAllByEventIdOrderByStartedAtDesc(Long eventId);

    Optional<ImportBatch> findFirstByEventIdAndScopeTypeAndFileSha256AndStatus(
            Long eventId,
            ImportScopeType scopeType,
            String fileSha256,
            ImportBatchStatus status
    );
}
