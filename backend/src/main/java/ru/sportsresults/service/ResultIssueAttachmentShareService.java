package ru.sportsresults.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.config.ResultIssueAttachmentProperties;
import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;
import ru.sportsresults.domain.ResultIssueAttachment;
import ru.sportsresults.domain.ResultIssueAttachmentShareGrant;
import ru.sportsresults.repository.ResultIssueAttachmentShareGrantRepository;
import ru.sportsresults.storage.attachments.AttachmentObjectStorage;
import ru.sportsresults.storage.attachments.PreparedObjectDownload;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
public class ResultIssueAttachmentShareService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResultIssueAttachmentShareService.class);
    private static final Set<String> INLINE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "application/pdf", "video/mp4"
    );

    private final ResultIssueAttachmentShareGrantRepository grantRepository;
    private final ResultIssueShareTokenService tokenService;
    private final ResultIssueShareAccessAuditService accessAuditService;
    private final AttachmentObjectStorage objectStorage;
    private final ResultIssueAttachmentProperties attachmentProperties;

    public ResultIssueAttachmentShareService(
            ResultIssueAttachmentShareGrantRepository grantRepository,
            ResultIssueShareTokenService tokenService,
            ResultIssueShareAccessAuditService accessAuditService,
            AttachmentObjectStorage objectStorage,
            ResultIssueAttachmentProperties attachmentProperties
    ) {
        this.grantRepository = grantRepository;
        this.tokenService = tokenService;
        this.accessAuditService = accessAuditService;
        this.objectStorage = objectStorage;
        this.attachmentProperties = attachmentProperties;
    }

    @Transactional(readOnly = true)
    public URI resolve(String rawToken) {
        if (!validTokenShape(rawToken)) {
            throw notFound();
        }
        ResultIssueAttachmentShareGrant grant = grantRepository.findByTokenHash(tokenService.hash(rawToken))
                .orElseThrow(ResultIssueAttachmentShareService::notFound);
        Instant now = Instant.now();
        if (grant.getRevokedAt() != null
                || grant.getBatch().getRevokedAt() != null
                || expired(grant.getExpiresAt(), now)
                || expired(grant.getBatch().getExpiresAt(), now)) {
            throw notFound();
        }
        ResultIssueAttachment attachment = grant.getAttachment();
        if (attachment.getUploadStatus() != AttachmentUploadStatus.UPLOADED
                || attachment.getScanStatus() != AttachmentScanStatus.CLEAN
                || attachment.getDeletedAt() != null) {
            throw notFound();
        }

        PreparedObjectDownload download;
        try {
            if (objectStorage.findObject(attachment.getStorageKey()).isEmpty()) {
                throw notFound();
            }
            download = objectStorage.prepareDownload(
                    attachment.getStorageKey(),
                    attachmentProperties.downloadAuthorizationTtl(),
                    contentDisposition(attachment)
            );
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Could not prepare shared attachment download for grantId={} attachmentId={}",
                    grant.getId(), attachment.getId()
            );
            throw new ResultIssueShareStorageUnavailableException(exception);
        }

        try {
            accessAuditService.record(grant.getId(), now);
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not record shared attachment access for grantId={}", grant.getId());
        }
        return download.url();
    }

    private static boolean validTokenShape(String token) {
        return token != null && token.length() >= 40 && token.length() <= 128
                && token.chars().allMatch(character -> Character.isLetterOrDigit(character)
                || character == '-' || character == '_');
    }

    private static boolean expired(Instant expiresAt, Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    private static String contentDisposition(ResultIssueAttachment attachment) {
        String contentType = attachment.getDetectedContentType() == null
                ? attachment.getContentType()
                : attachment.getDetectedContentType();
        boolean inline = contentType != null
                && INLINE_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT));
        String filename = sanitizeFilename(attachment.getOriginalFileName());
        ContentDisposition.Builder builder = inline ? ContentDisposition.inline() : ContentDisposition.attachment();
        return builder.filename(filename, StandardCharsets.UTF_8).build().toString();
    }

    private static String sanitizeFilename(String filename) {
        String sanitized = filename == null ? "attachment" : filename
                .replace('\r', '_')
                .replace('\n', '_')
                .replace('\0', '_')
                .strip();
        return sanitized.isEmpty() ? "attachment" : sanitized;
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("SHARED_ATTACHMENT_NOT_FOUND", "Shared attachment not found");
    }
}
