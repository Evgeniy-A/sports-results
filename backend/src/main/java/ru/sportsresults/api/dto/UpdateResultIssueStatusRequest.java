package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.sportsresults.domain.ResultIssueStatus;

public record UpdateResultIssueStatusRequest(
        @NotNull ResultIssueStatus expectedStatus,
        @NotNull ResultIssueStatus status,
        @Size(max = 1000) String comment
) {
}
