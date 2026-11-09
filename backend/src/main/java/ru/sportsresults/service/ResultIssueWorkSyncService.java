package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.api.dto.ResultIssueWorkspaceSyncDto;
import ru.sportsresults.repository.ResultIssueWorkspaceQueryRepository;

import java.time.Instant;
import java.util.List;

@Service
public class ResultIssueWorkSyncService {

    public static final int MANUAL_BATCH_SIZE = 100;

    private final ResultIssueWorkspaceQueryRepository queryRepository;
    private final ResultIssueWorkSyncExecutor executor;

    public ResultIssueWorkSyncService(
            ResultIssueWorkspaceQueryRepository queryRepository,
            ResultIssueWorkSyncExecutor executor
    ) {
        this.queryRepository = queryRepository;
        this.executor = executor;
    }

    public ResultIssueWorkspaceSyncDto syncSheet(long seriesId, int year, String actor) {
        return syncSheet(seriesId, year, null, actor);
    }

    public ResultIssueWorkspaceSyncDto syncSheet(long seriesId, int year, String cityScope, String actor) {
        List<Long> ids = queryRepository.findPendingWorkItemIds(seriesId, year, cityScope, MANUAL_BATCH_SIZE);
        int applied = 0;
        int conflicts = 0;
        int errors = 0;
        for (Long id : ids) {
            try {
                ResultIssueWorkSyncExecutor.SyncOutcome outcome = executor.sync(id, actor);
                if (outcome == ResultIssueWorkSyncExecutor.SyncOutcome.APPLIED) {
                    applied++;
                } else if (outcome == ResultIssueWorkSyncExecutor.SyncOutcome.CONFLICT) {
                    conflicts++;
                }
            } catch (RuntimeException exception) {
                errors++;
                executor.markFailed(id, errorCode(exception), actor);
            }
        }
        return new ResultIssueWorkspaceSyncDto(
                ids.size(),
                applied,
                conflicts,
                errors,
                queryRepository.countPending(seriesId, year, cityScope),
                Instant.now()
        );
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof RequestConflictException conflict) {
            return conflict.getCode();
        }
        if (exception instanceof InvalidRequestException invalid) {
            return invalid.getCode();
        }
        if (exception instanceof ResourceNotFoundException missing) {
            return missing.getCode();
        }
        return "JOURNAL_SYNC_FAILED";
    }
}
