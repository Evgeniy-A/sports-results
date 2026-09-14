package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.EventDocument;

import java.util.List;
import java.util.Optional;

public interface EventDocumentRepository extends JpaRepository<EventDocument, Long> {
    List<EventDocument> findAllByEventIdOrderByDisplayOrderAscIdAsc(Long eventId);
    List<EventDocument> findAllByEventIdAndPublicDocumentTrueOrderByDisplayOrderAscIdAsc(Long eventId);
    Optional<EventDocument> findByIdAndEventId(Long id, Long eventId);
    Optional<EventDocument> findByIdAndEventIdAndPublicDocumentTrue(Long id, Long eventId);
}
