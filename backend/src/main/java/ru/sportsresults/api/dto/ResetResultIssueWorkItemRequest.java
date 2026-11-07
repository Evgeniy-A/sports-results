package ru.sportsresults.api.dto;

import jakarta.validation.constraints.PositiveOrZero;

public record ResetResultIssueWorkItemRequest(@PositiveOrZero long expectedVersion) {
}
