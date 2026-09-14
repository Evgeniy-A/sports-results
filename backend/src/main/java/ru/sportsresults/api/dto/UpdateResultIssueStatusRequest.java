package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotNull;
import ru.sportsresults.domain.ResultIssueStatus;

public record UpdateResultIssueStatusRequest(
        @NotNull ResultIssueStatus expectedStatus,
        @NotNull ResultIssueStatus status
) {
}
