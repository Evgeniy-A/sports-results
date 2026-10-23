package ru.sportsresults.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceAdminUserEventsRequest(@NotNull List<@NotNull Long> eventIds) {
}
