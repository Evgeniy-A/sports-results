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
    @EntityGraph(attributePaths = "sportFormat")
    List<Race> findAllByEventIdOrderByDisplayOrderAsc(Long eventId);

    Optional<Race> findByEventIdAndSourceCode(Long eventId, String sourceCode);

    boolean existsByEventIdAndSlug(Long eventId, String slug);

    Optional<Race> findByIdAndEventId(Long id, Long eventId);

    Optional<Race> findByIdAndEventIdAndPublicVisibleTrueAndSportFormatPublicVisibleTrueAndResultsPublicationStatus(
            Long id,
            Long eventId,
            ResultsPublicationStatus resultsPublicationStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select race from Race race where race.id = :raceId and race.event.id = :eventId")
    Optional<Race> findByIdAndEventIdForUpdate(@Param("raceId") Long raceId, @Param("eventId") Long eventId);

    boolean existsBySportFormatId(Long sportFormatId);

    @Query("select race.event.id from Race race where race.id = :raceId")
    Optional<Long> findEventIdByRaceId(@Param("raceId") Long raceId);
}
