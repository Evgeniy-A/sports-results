package ru.sportsresults.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;
import ru.sportsresults.domain.ResultIssueAttachment;
import ru.sportsresults.repository.ResultIssueAttachmentRepository;
import ru.sportsresults.scanning.attachments.FakeAttachmentScanResult;
import ru.sportsresults.scanning.attachments.FakeAttachmentScannerProperties;
import ru.sportsresults.scanning.attachments.FakeAttachmentSecurityScanner;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultIssueAttachmentScanOrchestratorTest {

    @ParameterizedTest
    @EnumSource(FakeAttachmentScanResult.class)
    void appliesEveryFakeTerminalResultWithoutChangingUploadStatus(FakeAttachmentScanResult mode) {
        ResultIssueAttachmentRepository repository = mock(ResultIssueAttachmentRepository.class);
        ResultIssueAttachment attachment = uploadedPendingAttachment();
        when(repository.findById(41L)).thenReturn(Optional.of(attachment));
        when(repository.saveAndFlush(attachment)).thenReturn(attachment);
        var writer = new ResultIssueAttachmentScanService(repository);
        var scanner = new FakeAttachmentSecurityScanner(new FakeAttachmentScannerProperties(mode));
        var orchestrator = new ResultIssueAttachmentScanOrchestrator(
                repository, writer, Optional.of(scanner)
        );

        var result = orchestrator.scan(41L);

        assertThat(result.uploadStatus()).isEqualTo(AttachmentUploadStatus.UPLOADED);
        assertThat(result.scanStatus().name()).isEqualTo(mode.name());
        assertThat(result.scannedAt()).isNotNull();
        assertThat(attachment.getUploadStatus()).isEqualTo(AttachmentUploadStatus.UPLOADED);
        verify(repository).saveAndFlush(attachment);
    }

    @Test
    void failedOrInfectedAttachmentCanBeRetriedButCleanIsTerminal() {
        ResultIssueAttachmentRepository repository = mock(ResultIssueAttachmentRepository.class);
        ResultIssueAttachment attachment = uploadedPendingAttachment();
        when(repository.findById(41L)).thenReturn(Optional.of(attachment));
        when(repository.saveAndFlush(attachment)).thenReturn(attachment);
        var writer = new ResultIssueAttachmentScanService(repository);

        orchestrator(repository, writer, FakeAttachmentScanResult.SCAN_FAILED).scan(41L);
        assertThat(attachment.getScanStatus()).isEqualTo(AttachmentScanStatus.SCAN_FAILED);
        orchestrator(repository, writer, FakeAttachmentScanResult.INFECTED).scan(41L);
        assertThat(attachment.getScanStatus()).isEqualTo(AttachmentScanStatus.INFECTED);
        orchestrator(repository, writer, FakeAttachmentScanResult.CLEAN).scan(41L);
        assertThat(attachment.getScanStatus()).isEqualTo(AttachmentScanStatus.CLEAN);

        assertThatThrownBy(() -> orchestrator(
                repository, writer, FakeAttachmentScanResult.CLEAN
        ).scan(41L))
                .isInstanceOf(RequestConflictException.class)
                .hasMessageContaining("cannot be scanned again");
        assertThat(attachment.getUploadStatus()).isEqualTo(AttachmentUploadStatus.UPLOADED);
    }

    @Test
    void rejectsObjectsThatHaveNotCompletedUpload() {
        ResultIssueAttachmentRepository repository = mock(ResultIssueAttachmentRepository.class);
        ResultIssueAttachment attachment = uploadedPendingAttachment();
        attachment.setUploadStatus(AttachmentUploadStatus.PENDING_UPLOAD);
        when(repository.findById(41L)).thenReturn(Optional.of(attachment));
        var writer = new ResultIssueAttachmentScanService(repository);

        assertThatThrownBy(() -> orchestrator(
                repository, writer, FakeAttachmentScanResult.CLEAN
        ).scan(41L))
                .isInstanceOf(RequestConflictException.class)
                .hasMessageContaining("uploaded attachments");
    }

    @Test
    void leavesPendingAttachmentUntouchedWhenNoScannerIsConfigured() {
        ResultIssueAttachmentRepository repository = mock(ResultIssueAttachmentRepository.class);
        ResultIssueAttachment attachment = uploadedPendingAttachment();
        var writer = new ResultIssueAttachmentScanService(repository);
        var orchestrator = new ResultIssueAttachmentScanOrchestrator(
                repository, writer, Optional.empty()
        );

        assertThat(orchestrator.scanIfConfigured(41L)).isEmpty();
        assertThat(attachment.getScanStatus()).isEqualTo(AttachmentScanStatus.PENDING);
    }

    private static ResultIssueAttachmentScanOrchestrator orchestrator(
            ResultIssueAttachmentRepository repository,
            ResultIssueAttachmentScanService writer,
            FakeAttachmentScanResult mode
    ) {
        return new ResultIssueAttachmentScanOrchestrator(
                repository,
                writer,
                Optional.of(new FakeAttachmentSecurityScanner(new FakeAttachmentScannerProperties(mode)))
        );
    }

    private static ResultIssueAttachment uploadedPendingAttachment() {
        ResultIssueAttachment attachment = new ResultIssueAttachment();
        attachment.setStorageKey("result-issues/1/2/object");
        attachment.setContentType("video/mp4");
        attachment.setSizeBytes(125_000_000L);
        attachment.setUploadStatus(AttachmentUploadStatus.UPLOADED);
        attachment.setScanStatus(AttachmentScanStatus.PENDING);
        return attachment;
    }
}
