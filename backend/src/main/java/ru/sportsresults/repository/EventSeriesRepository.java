package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import ru.sportsresults.domain.EventSeries;

import java.util.Optional;

public interface EventSeriesRepository extends JpaRepository<EventSeries, Long> {
    boolean existsBySlug(String slug);

    Optional<EventSeries> findBySlug(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select series from EventSeries series where series.id = :seriesId")
    Optional<EventSeries> findByIdForUpdate(@Param("seriesId") Long seriesId);
}
