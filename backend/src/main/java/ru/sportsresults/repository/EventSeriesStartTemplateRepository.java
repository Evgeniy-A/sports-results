package ru.sportsresults.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.EventSeriesStartTemplate;

import java.util.List;
import java.util.Optional;

public interface EventSeriesStartTemplateRepository extends JpaRepository<EventSeriesStartTemplate, Long> {
    List<EventSeriesStartTemplate> findAllByEventSeriesIdOrderByDisplayOrderAscIdAsc(Long eventSeriesId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select template from EventSeriesStartTemplate template "
            + "where template.eventSeries.id = :eventSeriesId order by template.displayOrder, template.id")
    List<EventSeriesStartTemplate> findAllByEventSeriesIdForUpdate(@Param("eventSeriesId") Long eventSeriesId);

    Optional<EventSeriesStartTemplate> findByIdAndEventSeriesId(Long id, Long eventSeriesId);

    long countByEventSeriesId(Long eventSeriesId);
}
