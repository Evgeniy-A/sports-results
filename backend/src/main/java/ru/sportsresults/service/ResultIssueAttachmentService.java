package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.CreateResultIssueAttachmentRequest;
import ru.sportsresults.api.dto.ResultIssueAttachmentStatusDto;
import ru.sportsresults.api.dto.ResultIssueAttachmentUploadDto;
import ru.sportsresults.config.ResultIssueAttachmentProperties;
import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;
import ru.sportsresults.domain.ResultIssueAttachment;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.repository.ResultIssueAttachmentRepository;
import ru.sportsresults.repository.ResultIssueRequestRepository;
import ru.sportsresults.storage.attachments.AttachmentObjectStorage;
import ru.sportsresults.storage.attachments.PreparedObjectDownload;
import ru.sportsresults.storage.attachments.PreparedObjectUpload;
import ru.sportsresults.storage.attachments.StoredObjectMetadata;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ResultIssueAttachmentService {

    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            "exe", "msi", "bat", "cmd", "com", "scr", "ps1", "sh", "jar",
            "dll", "apk", "dmg", "pkg", "deb", "rpm"
    );
    private static final Set<String> FORBIDDEN_CONTENT_TYPES = Set.of(
            "application/x-msdownload",
            "application/x-executable",
            "application/vnd.microsoft.portable-executable",
            "application/java-archive",
            "application/x-sh"
    );

    private final ResultIssueAttachmentRepository attachmentRepository;
    private final ResultIssueRequestRepository issueRepository;
    private final ResultIssueAttachmentCapabilityService capabilityService;
    private final AttachmentObjectStorage objectStorage;
    private final ResultIssueAttachmentProperties properties;
    private final ResultIssueAttachmentScanOrchestrator scanOrchestrator;

    public ResultIssueAttachmentService(
            ResultIssueAttachmentRepository attachmentRepository,
            ResultIssueRequestRepository issueRepository,
            ResultIssueAttachmentCapabilityService capabilityService,
            AttachmentObjectStorage objectStorage,
            ResultIssueAttachmentProperties properties,
            ResultIssueAttachmentScanOrchestrator scanOrchestrator
    ) {
        this.attachmentRepository = attachmentRepository;
        this.issueRepository = issueRepository;
        this.capabilityService = capabilityService;
        this.objectStorage = objectStorage;
        this.properties = properties;
        this.scanOrchestrator = scanOrchestrator;
    }

    @Transactional
    public ResultIssueAttachmentUploadDto create(
            Long issueId,
            String capabilityToken,
            CreateResultIssueAttachmentRequest request
    ) {
        ResultIssueRequest issue = requireAuthorizedIssue(issueId, capabilityToken);
        validateMetadata(request);
        long currentCount = attachmentRepository.countByIssueRequest_IdAndUploadStatusNot(
                issueId, AttachmentUploadStatus.DELETED
        );
        if (currentCount >= properties.maxAttachmentsPerIssue()) {
            throw new RequestConflictException(
                    "ATTACHMENT_LIMIT_REACHED", "The attachment limit for this request has been reached"
            );
        }

        String storageKey = "result-issues/" + issue.getEvent().getId() + "/" + issueId + "/" + UUID.randomUUID();
        String contentType = normalizeContentType(request.contentType());
        PreparedObjectUpload preparedUpload = objectStorage.prepareUpload(
                storageKey,
                contentType,
                request.sizeBytes(),
                properties.uploadAuthorizationTtl()
        );
        Instant now = Instant.now();
        ResultIssueAttachment attachment = new ResultIssueAttachment();
        attachment.setIssueRequest(issue);
        attachment.setOriginalFileName(request.originalFileName().strip());
        attachment.setStorageKey(storageKey);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(request.sizeBytes());
        attachment.setUploadStatus(AttachmentUploadStatus.PENDING_UPLOAD);
        attachment.setScanStatus(AttachmentScanStatus.PENDING);
        attachment.setUploadAuthorizationExpiresAt(preparedUpload.expiresAt());
        attachment.setRetentionExpiresAt(now.plus(properties.retentionDays(), ChronoUnit.DAYS));
        attachment = attachmentRepository.saveAndFlush(attachment);

        return new ResultIssueAttachmentUploadDto(
                attachment.getId(),
                attachment.getUploadStatus(),
                attachment.getScanStatus(),
                preparedUpload.method(),
                preparedUpload.url().toString(),
                preparedUpload.requiredHeaders(),
                preparedUpload.expiresAt()
        );
    }

    @Transactional
    public ResultIssueAttachmentStatusDto confirmUpload(
            Long issueId,
            Long attachmentId,
            String capabilityToken
    ) {
        ResultIssueRequest issue = requireAuthorizedIssue(issueId, capabilityToken);
        ResultIssueAttachment attachment = requireAttachmentForIssue(issue, attachmentId);
        if (attachment.getUploadStatus() == AttachmentUploadStatus.UPLOADED) {
            return status(attachment);
        }
        if (attachment.getUploadStatus() != AttachmentUploadStatus.PENDING_UPLOAD) {
            throw new RequestConflictException(
                    "ATTACHMENT_NOT_PENDING", "The attachment is not waiting for upload confirmation"
            );
        }
        StoredObjectMetadata object = objectStorage.findObject(attachment.getStorageKey())
                .orElseThrow(() -> new RequestConflictException(
                        "ATTACHMENT_OBJECT_NOT_FOUND", "The uploaded object was not found in storage"
                ));
        if (object.sizeBytes() != attachment.getSizeBytes()
                || object.sizeBytes() > properties.maxFileSizeBytes()) {
            throw new RequestConflictException(
                    "ATTACHMENT_OBJECT_MISMATCH", "The uploaded object does not match declared metadata"
            );
        }

        attachment.setStorageEtag(normalize(object.eTag(), 512));
        attachment.setUploadStatus(AttachmentUploadStatus.UPLOADED);
        attachment.setScanStatus(AttachmentScanStatus.PENDING);
        attachment.setUploadedAt(Instant.now());
        attachment = attachmentRepository.saveAndFlush(attachment);
        scanOrchestrator.scanIfConfigured(attachment.getId());
        return status(attachment);
    }

    @Transactional
    public ResultIssueAttachmentUploadDto refreshUploadAuthorization(
            Long issueId,
            Long attachmentId,
            String capabilityToken
    ) {
        ResultIssueRequest issue = requireAuthorizedIssue(issueId, capabilityToken);
        ResultIssueAttachment attachment = requireAttachmentForIssue(issue, attachmentId);
        if (attachment.getUploadStatus() != AttachmentUploadStatus.PENDING_UPLOAD) {
            throw new RequestConflictException(
                    "ATTACHMENT_NOT_PENDING", "The attachment is not waiting for upload"
            );
        }
        PreparedObjectUpload preparedUpload = objectStorage.prepareUpload(
                attachment.getStorageKey(),
                attachment.getContentType(),
                attachment.getSizeBytes(),
                properties.uploadAuthorizationTtl()
        );
        attachment.setUploadAuthorizationExpiresAt(preparedUpload.expiresAt());
        attachmentRepository.saveAndFlush(attachment);
        return new ResultIssueAttachmentUploadDto(
                attachment.getId(),
                attachment.getUploadStatus(),
                attachment.getScanStatus(),
                preparedUpload.method(),
                preparedUpload.url().toString(),
                preparedUpload.requiredHeaders(),
                preparedUpload.expiresAt()
        );
    }

    @Transactional(readOnly = true)
    public PreparedObjectDownload prepareAuthorizedDownload(Long attachmentId) {
        ResultIssueAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ATTACHMENT_NOT_FOUND", "Attachment not found"
                ));
        if (attachment.getUploadStatus() != AttachmentUploadStatus.UPLOADED
                || attachment.getScanStatus() != AttachmentScanStatus.CLEAN) {
            throw new RequestConflictException(
                    "ATTACHMENT_NOT_AVAILABLE", "Attachment is not available for download"
            );
        }
        return objectStorage.prepareDownload(
                attachment.getStorageKey(), properties.downloadAuthorizationTtl()
        );
    }

    @Transactional
    public void deleteBinary(Long attachmentId) {
        ResultIssueAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ATTACHMENT_NOT_FOUND", "Attachment not found"
                ));
        if (attachment.getUploadStatus() == AttachmentUploadStatus.DELETED) {
            return;
        }
        objectStorage.deleteObject(attachment.getStorageKey());
        attachment.setUploadStatus(AttachmentUploadStatus.DELETED);
        attachment.setDeletedAt(Instant.now());
        attachmentRepository.saveAndFlush(attachment);
    }

    private ResultIssueRequest requireAuthorizedIssue(Long issueId, String capabilityToken) {
        ResultIssueRequest issue = issueRepository.findById(issueId)
                .orElseThrow(ResultIssueAttachmentService::accessDenied);
        if (!capabilityService.permits(issue, capabilityToken)) {
            throw accessDenied();
        }
        return issue;
    }

    private ResultIssueAttachment requireAttachmentForIssue(ResultIssueRequest issue, Long attachmentId) {
        ResultIssueAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(ResultIssueAttachmentService::accessDenied);
        if (!attachment.getIssueRequest().getId().equals(issue.getId())) {
            throw accessDenied();
        }
        return attachment;
    }

    private void validateMetadata(CreateResultIssueAttachmentRequest request) {
        if (request.sizeBytes() > properties.maxFileSizeBytes()) {
            throw new InvalidRequestException(
                    "ATTACHMENT_TOO_LARGE", "Attachment exceeds the configured maximum size"
            );
        }
        String fileName = request.originalFileName().strip();
        if (fileName.indexOf('\0') >= 0 || FORBIDDEN_EXTENSIONS.contains(extension(fileName))) {
            throw new InvalidRequestException(
                    "ATTACHMENT_TYPE_NOT_ALLOWED", "Executable attachments are not allowed"
            );
        }
        String contentType = normalizeContentType(request.contentType());
        if (contentType != null && FORBIDDEN_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidRequestException(
                    "ATTACHMENT_TYPE_NOT_ALLOWED", "Executable attachments are not allowed"
            );
        }
    }

    private static String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private static String normalizeContentType(String contentType) {
        return normalize(contentType == null ? null : contentType.toLowerCase(Locale.ROOT), 255);
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static ResultIssueAttachmentStatusDto status(ResultIssueAttachment attachment) {
        return new ResultIssueAttachmentStatusDto(
                attachment.getId(), attachment.getUploadStatus(), attachment.getScanStatus(), attachment.getUploadedAt()
        );
    }

    private static ResourceNotFoundException accessDenied() {
        return new ResourceNotFoundException(
                "RESULT_ISSUE_ATTACHMENT_ACCESS_DENIED", "Attachment upload access was not found"
        );
    }
}
