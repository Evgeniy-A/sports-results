package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotNull;
import ru.sportsresults.domain.ResultIssueArchiveReason;

public record ArchiveResultIssueRequest(
        @NotNull ResultIssueArchiveReason reason
) {
}
