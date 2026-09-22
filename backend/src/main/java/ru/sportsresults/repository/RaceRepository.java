package ru.sportsresults.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.ResultsPublicationStatus;

import java.util.List;
import java.util.Optional;

public interface RaceRepository extends JpaRepository<Race, Long> {
    List<Race> findAllByEventIdOrderByDisplayOrderAsc(Long eventId);

    List<Race> findAllByEventIdOrderByDisplayOrderAscIdAsc(Long eventId);

    Optional<Race> findByEventIdAndSourceCode(Long eventId, String sourceCode);

    boolean existsByEventIdAndSourceCode(Long eventId, String sourceCode);

    boolean existsByEventIdAndPublicVisibleTrueAndResultsPublicationStatus(
            Long eventId,
            ResultsPublicationStatus resultsPublicationStatus
    );

    boolean existsByEventIdAndSlug(Long eventId, String slug);

    Optional<Race> findByIdAndEventId(Long id, Long eventId);

    Optional<Race> findByIdAndEventIdAndPublicVisibleTrueAndResultsPublicationStatus(
            Long id,
            Long eventId,
            ResultsPublicationStatus resultsPublicationStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select race from Race race where race.id = :raceId and race.event.id = :eventId")
    Optional<Race> findByIdAndEventIdForUpdate(@Param("raceId") Long raceId, @Param("eventId") Long eventId);

    @Query("select race.event.id from Race race where race.id = :raceId")
    Optional<Long> findEventIdByRaceId(@Param("raceId") Long raceId);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM categories WHERE race_id = :raceId)
                OR EXISTS (SELECT 1 FROM checkpoints WHERE race_id = :raceId)
                OR EXISTS (SELECT 1 FROM start_clusters WHERE race_id = :raceId)
                OR EXISTS (SELECT 1 FROM import_batches WHERE race_id = :raceId)
                OR EXISTS (SELECT 1 FROM import_operation_races WHERE race_id = :raceId)
                OR EXISTS (SELECT 1 FROM import_operation_items WHERE target_race_id = :raceId)
                OR EXISTS (SELECT 1 FROM result_recalculation_operation_races WHERE race_id = :raceId)
                OR EXISTS (SELECT 1 FROM race_result_publication_history WHERE race_id = :raceId)
            """, nativeQuery = true)
    boolean hasDeleteBlockingDependencies(@Param("raceId") Long raceId);
}
