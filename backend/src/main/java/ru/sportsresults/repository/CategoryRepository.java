package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByRaceIdOrderByDisplayOrderAsc(Long raceId);

    Optional<Category> findByRaceIdAndSourceName(Long raceId, String sourceName);

    Optional<Category> findByIdAndRaceId(Long id, Long raceId);

    List<Category> findAllByRaceEventId(Long eventId);

    @Query("""
            select category
            from Category category
            join fetch category.race race
            where race.event.id = :eventId
            order by race.displayOrder, category.displayOrder, category.displayName
            """)
    List<Category> findOptionsByEventId(@Param("eventId") Long eventId);
}
