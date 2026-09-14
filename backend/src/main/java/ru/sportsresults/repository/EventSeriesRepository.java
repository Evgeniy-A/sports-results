package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.EventSeries;

import java.util.Optional;

public interface EventSeriesRepository extends JpaRepository<EventSeries, Long> {
    Optional<EventSeries> findBySlug(String slug);
}
