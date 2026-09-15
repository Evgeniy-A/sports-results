package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.EventPublicationStatus;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long>, EventCatalogRepository {
    boolean existsBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from Event event where event.id = :eventId")
    Optional<Event> findByIdForUpdate(@Param("eventId") Long eventId);

    @EntityGraph(attributePaths = "eventSeries")
    Optional<Event> findBySlug(String slug);

    @EntityGraph(attributePaths = "eventSeries")
    List<Event> findAllByPublicationStatusOrderByStartsAtDesc(EventPublicationStatus publicationStatus);

    @EntityGraph(attributePaths = "eventSeries")
    Optional<Event> findByIdAndPublicationStatus(Long id, EventPublicationStatus publicationStatus);

    @EntityGraph(attributePaths = "eventSeries")
    Optional<Event> findBySlugAndPublicationStatus(String slug, EventPublicationStatus publicationStatus);

    @EntityGraph(attributePaths = "eventSeries")
    List<Event> findAllByPublicationStatusAndEventSeriesActiveTrue(EventPublicationStatus publicationStatus);
}
