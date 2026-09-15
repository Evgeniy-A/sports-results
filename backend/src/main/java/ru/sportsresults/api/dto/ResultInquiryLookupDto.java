package ru.sportsresults.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ru.sportsresults.domain.ResultInquiryAvailability;
import ru.sportsresults.domain.ResultInquiryLookupState;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResultInquiryLookupDto(
        ResultInquiryLookupState lookupState,
        ResultInquiryAvailability inquiryAvailability,
        String bib,
        String participantDisplayName,
        Long raceId,
        String raceDisplayName,
        String startDisplayName,
        Long publicResultId,
        boolean missingResultActionAvailable,
        Instant deadline,
        String contactEmail,
        ActiveResultIssueDto activeIssue
) {
}
