package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.ResultIssueHistory;
import ru.sportsresults.domain.ResultIssueHistoryAction;

import java.util.List;

public interface ResultIssueHistoryRepository extends JpaRepository<ResultIssueHistory, Long> {
    List<ResultIssueHistory> findAllByIssueRequest_IdOrderByCreatedAtAscIdAsc(Long issueId);

    long countByIssueRequest_IdAndAction(Long issueId, ResultIssueHistoryAction action);
}
