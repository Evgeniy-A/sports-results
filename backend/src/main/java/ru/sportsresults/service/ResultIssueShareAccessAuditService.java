package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.repository.ResultIssueAttachmentShareGrantRepository;

import java.time.Instant;
import java.util.UUID;

@Service
public class ResultIssueShareAccessAuditService {

    private final ResultIssueAttachmentShareGrantRepository grantRepository;

    public ResultIssueShareAccessAuditService(ResultIssueAttachmentShareGrantRepository grantRepository) {
        this.grantRepository = grantRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID grantId, Instant accessedAt) {
        grantRepository.recordAccess(grantId, accessedAt);
    }
}
