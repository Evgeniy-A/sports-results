package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.ResultIssueAttachmentShareGrant;
import ru.sportsresults.domain.ResultIssueShareBatch;
import ru.sportsresults.repository.ResultIssueAttachmentRepository;
import ru.sportsresults.repository.ResultIssueAttachmentShareGrantRepository;
import ru.sportsresults.repository.ResultIssueShareBatchRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ResultIssueSharePersistenceService {

    private final ResultIssueShareBatchRepository batchRepository;
    private final ResultIssueAttachmentShareGrantRepository grantRepository;
    private final ResultIssueAttachmentRepository attachmentRepository;

    public ResultIssueSharePersistenceService(
            ResultIssueShareBatchRepository batchRepository,
            ResultIssueAttachmentShareGrantRepository grantRepository,
            ResultIssueAttachmentRepository attachmentRepository
    ) {
        this.batchRepository = batchRepository;
        this.grantRepository = grantRepository;
        this.attachmentRepository = attachmentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(
            UUID batchId,
            String actor,
            Instant createdAt,
            Instant expiresAt,
            long matchedIssueCount,
            List<ResultIssueShareGrantDraft> drafts
    ) {
        ResultIssueShareBatch batch = new ResultIssueShareBatch();
        batch.setId(batchId);
        batch.setCreatedBy(actor);
        batch.setCreatedAt(createdAt);
        batch.setExpiresAt(expiresAt);
        batch.setMatchedIssueCount(matchedIssueCount);
        batch.setGrantCount(drafts.size());
        batchRepository.save(batch);

        List<ResultIssueAttachmentShareGrant> grants = new ArrayList<>(drafts.size());
        for (ResultIssueShareGrantDraft draft : drafts) {
            ResultIssueAttachmentShareGrant grant = new ResultIssueAttachmentShareGrant();
            grant.setId(UUID.randomUUID());
            grant.setBatch(batch);
            grant.setAttachment(attachmentRepository.getReferenceById(draft.attachmentId()));
            grant.setTokenHash(draft.tokenHash());
            grant.setCreatedAt(createdAt);
            grant.setExpiresAt(expiresAt);
            grants.add(grant);
        }
        grantRepository.saveAllAndFlush(grants);
    }
}
