package ru.sportsresults.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface ResultSearchRepository {
    Page<ResultListProjection> search(
            ResultSearchCriteria criteria,
            int page,
            int size,
            ResultSortField sortField,
            Sort.Direction direction
    );

    List<ResultLeaderTimesProjection> findLeaderTimes(ResultSearchCriteria criteria);
}
