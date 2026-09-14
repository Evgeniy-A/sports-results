package ru.sportsresults.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

public interface ResultIssueRequestSearchRepository {
    Page<AdminResultIssueListProjection> searchAdmin(
            ResultIssueRequestSearchCriteria criteria,
            int page,
            int size
    );

    Page<GlobalResultIssueJournalProjection> searchJournal(
            ResultIssueJournalFilter filter,
            int page,
            int size,
            ResultIssueJournalSort sort,
            Sort.Direction direction
    );

    long countJournal(ResultIssueJournalFilter filter);

    java.util.List<GlobalResultIssueExportProjection> findJournalExportBatch(
            ResultIssueJournalFilter filter,
            int offset,
            int size,
            ResultIssueJournalSort sort,
            Sort.Direction direction
    );
}
