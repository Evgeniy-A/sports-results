package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.ResultIssueAttachmentShareGrantDto;
import ru.sportsresults.api.dto.ResultIssueShareBatchDto;
import ru.sportsresults.domain.ResultIssueAttachmentShareGrant;
import ru.sportsresults.domain.ResultIssueShareBatch;
import ru.sportsresults.repository.ResultIssueAttachmentShareGrantRepository;
import ru.sportsresults.repository.ResultIssueShareBatchRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class ResultIssueShareAdminService {

    private final ResultIssueShareBatchRepository batchRepository;
    private final ResultIssueAttachmentShareGrantRepository grantRepository;

    public ResultIssueShareAdminService(
            ResultIssueShareBatchRepository batchRepository,
            ResultIssueAttachmentShareGrantRepository grantRepository
    ) {
        this.batchRepository = batchRepository;
        this.grantRepository = grantRepository;
    }

    @Transactional(readOnly = true)
    public ResultIssueShareBatchDto getBatch(UUID batchId) {
        return toDto(requireBatch(batchId));
    }

    @Transactional
    public ResultIssueShareBatchDto revokeBatch(UUID batchId, String actor) {
        ResultIssueShareBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(ResultIssueShareAdminService::batchNotFound);
        if (batch.getRevokedAt() == null) {
            batch.setRevokedAt(Instant.now());
            batch.setRevokedBy(normalizeActor(actor));
            batchRepository.saveAndFlush(batch);
        }
        return toDto(batch);
    }

    @Transactional
    public ResultIssueAttachmentShareGrantDto revokeGrant(UUID grantId, String actor) {
        ResultIssueAttachmentShareGrant grant = grantRepository.findByIdForUpdate(grantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RESULT_ISSUE_SHARE_GRANT_NOT_FOUND", "Attachment share grant not found"
                ));
        if (grant.getRevokedAt() == null) {
            grant.setRevokedAt(Instant.now());
            grant.setRevokedBy(normalizeActor(actor));
            grantRepository.saveAndFlush(grant);
        }
        return toDto(grant);
    }

    private ResultIssueShareBatch requireBatch(UUID batchId) {
        return batchRepository.findById(batchId).orElseThrow(ResultIssueShareAdminService::batchNotFound);
    }

    private ResultIssueShareBatchDto toDto(ResultIssueShareBatch batch) {
        return new ResultIssueShareBatchDto(
                batch.getId(),
                batch.getCreatedBy(),
                batch.getCreatedAt(),
                batch.getExpiresAt(),
                batch.getRevokedAt(),
                batch.getRevokedBy(),
                batch.getMatchedIssueCount(),
                batch.getGrantCount(),
                grantRepository.countByBatch_IdAndAccessCountGreaterThan(batch.getId(), 0)
        );
    }

    private static ResultIssueAttachmentShareGrantDto toDto(ResultIssueAttachmentShareGrant grant) {
        return new ResultIssueAttachmentShareGrantDto(
                grant.getId(),
                grant.getBatch().getId(),
                grant.getAttachment().getId(),
                grant.getCreatedAt(),
                grant.getExpiresAt(),
                grant.getRevokedAt(),
                grant.getRevokedBy(),
                grant.getLastAccessedAt(),
                grant.getAccessCount()
        );
    }

    private static String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRequestException("INVALID_ACTOR", "Authenticated actor is required");
        }
        String normalized = actor.strip();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private static ResourceNotFoundException batchNotFound() {
        return new ResourceNotFoundException(
                "RESULT_ISSUE_SHARE_BATCH_NOT_FOUND", "Result issue share batch not found"
        );
    }
}
