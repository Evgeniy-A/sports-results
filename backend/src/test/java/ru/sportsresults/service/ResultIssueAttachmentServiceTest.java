package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.sportsresults.config.ResultIssueAttachmentProperties;
import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;
import ru.sportsresults.domain.ResultIssueAttachment;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.repository.ResultIssueAttachmentRepository;
import ru.sportsresults.repository.ResultIssueRequestRepository;
import ru.sportsresults.storage.attachments.AttachmentObjectStorage;
import ru.sportsresults.storage.attachments.StoredObjectMetadata;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultIssueAttachmentServiceTest {

    @Test
    void confirmedUploadRunsConfiguredScannerAndReturnsCleanStatus() {
        ResultIssueAttachmentRepository attachmentRepository = mock(ResultIssueAttachmentRepository.class);
        ResultIssueRequestRepository issueRepository = mock(ResultIssueRequestRepository.class);
        ResultIssueAttachmentCapabilityService capabilityService = mock(ResultIssueAttachmentCapabilityService.class);
        AttachmentObjectStorage objectStorage = mock(AttachmentObjectStorage.class);
        ResultIssueAttachmentScanOrchestrator scanOrchestrator = mock(ResultIssueAttachmentScanOrchestrator.class);
        ResultIssueAttachmentProperties properties = new ResultIssueAttachmentProperties(
                10_737_418_240L,
                10,
                Duration.ofHours(1),
                Duration.ofMinutes(15),
                Duration.ofMinutes(5),
                90
        );
        ResultIssueRequest issue = mock(ResultIssueRequest.class);
        when(issue.getId()).thenReturn(2L);
        ResultIssueAttachment attachment = new ResultIssueAttachment();
        ReflectionTestUtils.setField(attachment, "id", 41L);
        attachment.setIssueRequest(issue);
        attachment.setOriginalFileName("proof.jpeg");
        attachment.setStorageKey("result-issues/1/2/object");
        attachment.setContentType("image/jpeg");
        attachment.setSizeBytes(2_428_659L);
        attachment.setUploadStatus(AttachmentUploadStatus.PENDING_UPLOAD);
        attachment.setScanStatus(AttachmentScanStatus.PENDING);

        when(issueRepository.findById(2L)).thenReturn(Optional.of(issue));
        when(capabilityService.permits(issue, "token")).thenReturn(true);
        when(attachmentRepository.findById(41L)).thenReturn(Optional.of(attachment));
        when(objectStorage.findObject(attachment.getStorageKey())).thenReturn(Optional.of(
                new StoredObjectMetadata(attachment.getSizeBytes(), "image/jpeg", "etag")
        ));
        when(attachmentRepository.saveAndFlush(attachment)).thenReturn(attachment);
        when(scanOrchestrator.scanIfConfigured(41L)).thenAnswer(invocation -> {
            attachment.setScanStatus(AttachmentScanStatus.CLEAN);
            attachment.setScannedAt(Instant.now());
            return Optional.of(new AttachmentScanExecution(
                    41L, AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.CLEAN,
                    attachment.getScannedAt()
            ));
        });
        ResultIssueAttachmentService service = new ResultIssueAttachmentService(
                attachmentRepository,
                issueRepository,
                capabilityService,
                objectStorage,
                properties,
                scanOrchestrator
        );

        var status = service.confirmUpload(2L, 41L, "token");

        assertThat(status.uploadStatus()).isEqualTo(AttachmentUploadStatus.UPLOADED);
        assertThat(status.scanStatus()).isEqualTo(AttachmentScanStatus.CLEAN);
        assertThat(status.uploadedAt()).isNotNull();
        verify(scanOrchestrator).scanIfConfigured(41L);
    }
}
