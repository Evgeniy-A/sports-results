package ru.sportsresults.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

public interface ResultSearchRepository {
    Page<ResultListProjection> search(
            ResultSearchCriteria criteria,
            int page,
            int size,
            ResultSortField sortField,
            Sort.Direction direction
    );
}
