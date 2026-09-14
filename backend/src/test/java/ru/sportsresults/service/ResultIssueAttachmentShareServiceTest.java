package ru.sportsresults.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.sportsresults.config.ResultIssueAttachmentProperties;
import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;
import ru.sportsresults.domain.ResultIssueAttachment;
import ru.sportsresults.domain.ResultIssueAttachmentShareGrant;
import ru.sportsresults.domain.ResultIssueShareBatch;
import ru.sportsresults.repository.ResultIssueAttachmentShareGrantRepository;
import ru.sportsresults.storage.attachments.AttachmentObjectStorage;
import ru.sportsresults.storage.attachments.PreparedObjectDownload;
import ru.sportsresults.storage.attachments.StoredObjectMetadata;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultIssueAttachmentShareServiceTest {

    private ResultIssueAttachmentShareGrantRepository repository;
    private AttachmentObjectStorage storage;
    private ResultIssueShareAccessAuditService accessAudit;
    private ResultIssueShareTokenService tokenService;
    private ResultIssueAttachmentShareService service;
    private ResultIssueAttachmentShareGrant grant;

    @BeforeEach
    void setUp() {
        repository = mock(ResultIssueAttachmentShareGrantRepository.class);
        storage = mock(AttachmentObjectStorage.class);
        accessAudit = mock(ResultIssueShareAccessAuditService.class);
        tokenService = new ResultIssueShareTokenService();
        service = new ResultIssueAttachmentShareService(
                repository,
                tokenService,
                accessAudit,
                storage,
                new ResultIssueAttachmentProperties(
                        1_000_000_000L, 10, Duration.ofMinutes(15), Duration.ofMinutes(15),
                        Duration.ofMinutes(5), 90
                )
        );
        ResultIssueShareBatch batch = new ResultIssueShareBatch();
        batch.setId(UUID.randomUUID());
        batch.setCreatedAt(Instant.now());
        ResultIssueAttachment attachment = new ResultIssueAttachment();
        attachment.setStorageKey("private/key");
        attachment.setOriginalFileName("финиш\r\nvideo.mp4");
        attachment.setContentType("video/mp4");
        attachment.setDetectedContentType("video/mp4");
        attachment.setSizeBytes(42);
        attachment.setUploadStatus(AttachmentUploadStatus.UPLOADED);
        attachment.setScanStatus(AttachmentScanStatus.CLEAN);
        grant = new ResultIssueAttachmentShareGrant();
        grant.setId(UUID.randomUUID());
        grant.setBatch(batch);
        grant.setAttachment(attachment);
        grant.setCreatedAt(Instant.now());
    }

    @Test
    void resolvesToShortGetWithoutProxyingAndUsesSafeInlineFilename() {
        String token = "A".repeat(43);
        when(repository.findByTokenHash(tokenService.hash(token))).thenReturn(Optional.of(grant));
        when(storage.findObject("private/key"))
                .thenReturn(Optional.of(new StoredObjectMetadata(42, "video/mp4", "etag")));
        when(storage.prepareDownload(eq("private/key"), eq(Duration.ofMinutes(5)), anyString()))
                .thenReturn(new PreparedObjectDownload(
                        URI.create("https://storage.test/private/key?short-lived=true"),
                        Instant.now().plus(Duration.ofMinutes(5))
                ));

        assertThat(service.resolve(token).toString())
                .isEqualTo("https://storage.test/private/key?short-lived=true");
        ArgumentCaptor<String> disposition = ArgumentCaptor.forClass(String.class);
        verify(storage).prepareDownload(eq("private/key"), eq(Duration.ofMinutes(5)), disposition.capture());
        assertThat(disposition.getValue())
                .startsWith("inline")
                .doesNotContain("\r", "\n")
                .contains("video.mp4");
        verify(accessAudit).record(eq(grant.getId()), org.mockito.ArgumentMatchers.any(Instant.class));
    }

    @Test
    void mapsTemporaryStorageFailureToControlledShareFailureWithoutRevokingGrant() {
        String token = "B".repeat(43);
        when(repository.findByTokenHash(tokenService.hash(token))).thenReturn(Optional.of(grant));
        when(storage.findObject("private/key"))
                .thenReturn(Optional.of(new StoredObjectMetadata(42, "video/mp4", "etag")));
        when(storage.prepareDownload(eq("private/key"), eq(Duration.ofMinutes(5)), anyString()))
                .thenThrow(new IllegalStateException("secret provider detail"));

        assertThatThrownBy(() -> service.resolve(token))
                .isInstanceOf(ResultIssueShareStorageUnavailableException.class);
        assertThat(grant.getRevokedAt()).isNull();
        assertThat(grant.getBatch().getRevokedAt()).isNull();
    }
}
