package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;

import java.util.List;

public interface AdminChangeLogRepository extends JpaRepository<AdminChangeLog, Long> {
    List<AdminChangeLog> findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
            AuditEntityType entityType,
            Long entityId
    );
}
