package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.ResultIssueWorkItem;
import ru.sportsresults.repository.AdminChangeLogRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ResultIssueWorkItemAuditService {

    private final AdminChangeLogRepository repository;

    public ResultIssueWorkItemAuditService(AdminChangeLogRepository repository) {
        this.repository = repository;
    }

    public Change change(String field, Object oldValue, Object newValue) {
        return Objects.equals(oldValue, newValue) ? null : new Change(field, oldValue, newValue);
    }

    public void save(ResultIssueWorkItem item, String actor, Change... candidates) {
        List<AdminChangeLog> logs = new ArrayList<>();
        for (Change candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            AdminChangeLog log = new AdminChangeLog();
            log.setActor(actor);
            log.setEntityType(AuditEntityType.RESULT_ISSUE_WORK_ITEM);
            log.setEntityId(item.getId());
            log.setFieldName(candidate.field());
            log.setOldValue(stringValue(candidate.oldValue()));
            log.setNewValue(stringValue(candidate.newValue()));
            logs.add(log);
        }
        if (!logs.isEmpty()) {
            repository.saveAll(logs);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public record Change(String field, Object oldValue, Object newValue) {
    }
}
