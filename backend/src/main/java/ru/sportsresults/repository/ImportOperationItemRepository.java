package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.ImportOperationItem;

import java.util.List;
import java.util.UUID;

public interface ImportOperationItemRepository extends JpaRepository<ImportOperationItem, Long> {
    List<ImportOperationItem> findAllByOperationIdOrderBySourceRowNumberAsc(UUID operationId);
}
