package ru.sportsresults.repository;

import ru.sportsresults.service.InvalidRequestException;

import java.util.Arrays;

public enum ResultIssueJournalSort {
    CREATED_AT("createdAt", "createdAt"),
    EVENT_DATE("eventDate", "snapshotEventStartsAt"),
    ISSUE_ID("issueId", "id"),
    STATUS("status", "status"),
    QUEUE_ARCHIVED_AT("queueArchivedAt", "queueArchivedAt");

    private final String apiName;
    private final String entityAttribute;

    ResultIssueJournalSort(String apiName, String entityAttribute) {
        this.apiName = apiName;
        this.entityAttribute = entityAttribute;
    }

    public String apiName() {
        return apiName;
    }

    public String entityAttribute() {
        return entityAttribute;
    }

    public static ResultIssueJournalSort parse(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.apiName.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new InvalidRequestException(
                        "INVALID_JOURNAL_SORT", "Unsupported journal sort field: " + value
                ));
    }
}
