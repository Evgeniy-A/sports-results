package ru.sportsresults.domain;

import java.util.Set;

public enum ResultIssueStatus {
    NEW,
    IN_PROGRESS,
    RESOLVED,
    REJECTED;

    private static final Set<ResultIssueStatus> ACTIVE = Set.of(NEW, IN_PROGRESS);

    public static Set<ResultIssueStatus> activeStatuses() {
        return ACTIVE;
    }
}
