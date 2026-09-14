package ru.sportsresults.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueQueueScope;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ResultIssueJournalExportRequest(
        Long issueId,
        Long eventId,
        String location,
        LocalDate eventDateFrom,
        LocalDate eventDateTo,
        Instant createdFrom,
        Instant createdTo,
        Long sportFormatId,
        String sportFormatCode,
        Long raceId,
        String raceCode,
        String bib,
        String participant,
        ResultIssueType issueType,
        ResultCorrectionReason correctionReason,
        List<ResultIssueStatus> statuses,
        ResultIssueQueueScope queueScope,
        ResultIssueArchiveReason queueArchiveReason,
        UUID queueArchivedImportOperationId,
        String sort,
        String direction,
        @Min(1) @Max(365) Integer shareLifetimeDays,
        Boolean noExpiry
) {
}
