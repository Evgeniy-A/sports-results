package ru.sportsresults.repository;

import org.springframework.data.domain.Sort;

public record ResultIssueJournalQuery(
        ResultIssueJournalFilter filter,
        ResultIssueJournalSort sort,
        Sort.Direction direction
) {
}
