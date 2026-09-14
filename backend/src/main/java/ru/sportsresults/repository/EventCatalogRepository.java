package ru.sportsresults.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.EventPublicationStatus;
import ru.sportsresults.domain.ResultsPublicationStatus;

public interface EventCatalogRepository {
    Page<Event> searchPublished(EventCatalogCriteria criteria, int page, int size);

    Page<Event> searchAdmin(
            String name,
            String location,
            EventPublicationStatus publicationStatus,
            ResultsPublicationStatus resultsPublicationStatus,
            Pageable pageable
    );
}
