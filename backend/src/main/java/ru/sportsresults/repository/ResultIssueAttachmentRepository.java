package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.AttachmentUploadStatus;
import ru.sportsresults.domain.ResultIssueAttachment;

import java.util.List;
import java.util.Optional;

public interface ResultIssueAttachmentRepository extends JpaRepository<ResultIssueAttachment, Long> {
    long countByIssueRequest_Id(Long issueRequestId);
    long countByIssueRequest_IdAndUploadStatusNot(Long issueRequestId, AttachmentUploadStatus uploadStatus);
    List<ResultIssueAttachment> findAllByIssueRequest_IdOrderByCreatedAtAscIdAsc(Long issueRequestId);
    Optional<ResultIssueAttachment> findByIdAndIssueRequest_Id(Long attachmentId, Long issueRequestId);

    @Query("""
            select new ru.sportsresults.repository.ResultIssueAttachmentExportProjection(
                attachment.id, attachment.issueRequest.id, attachment.originalFileName,
                attachment.contentType, attachment.detectedContentType, attachment.sizeBytes,
                attachment.uploadStatus, attachment.scanStatus, attachment.deletedAt
            )
            from ResultIssueAttachment attachment
            where attachment.issueRequest.id in :issueIds
            order by attachment.issueRequest.id, attachment.id
            """)
    List<ResultIssueAttachmentExportProjection> findExportMetadataByIssueIds(
            @Param("issueIds") List<Long> issueIds
    );
}
