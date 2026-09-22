package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.Registration;

import java.util.List;
import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {
    @EntityGraph(attributePaths = {
            "race", "race.event", "race.event.eventSeries",
            "category", "cluster", "importBatch"
    })
    @Query("select registration from Registration registration "
            + "where registration.race.id = :raceId and registration.bib = :bib "
            + "and registration.retiredAt is null")
    List<Registration> findAllByRaceIdAndBib(@Param("raceId") Long raceId, @Param("bib") String bib);

    @EntityGraph(attributePaths = {
            "race", "race.event", "race.event.eventSeries",
            "category", "importBatch"
    })
    @Query("select registration from Registration registration "
            + "where registration.race.event.id = :eventId and registration.bib = :bib "
            + "and registration.retiredAt is null")
    List<Registration> findAllByRaceEventIdAndBib(@Param("eventId") Long eventId, @Param("bib") String bib);

    /**
     * Canonical current-dataset predicate used by import planning.
     */
    @EntityGraph(attributePaths = {"race", "cluster"})
    @Query("select registration from Registration registration "
            + "where registration.race.event.id = :eventId and registration.retiredAt is null")
    List<Registration> findAllCurrentByEventId(@Param("eventId") Long eventId);

    @EntityGraph(attributePaths = {"race", "category", "cluster", "importBatch"})
    @Query("select registration from Registration registration "
            + "where registration.race.id in :raceIds and registration.retiredAt is null")
    List<Registration> findAllCurrentByRaceIds(@Param("raceIds") List<Long> raceIds);

    @Query("select distinct registration.race.id from Registration registration "
            + "where registration.race.id in :raceIds and registration.retiredAt is null")
    List<Long> findRaceIdsWithCurrentRegistrations(@Param("raceIds") List<Long> raceIds);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByRaceId(Long raceId);

    @Query("select count(registration) from Registration registration "
            + "where registration.race.event.id = :eventId and registration.retiredAt is null")
    long countByRaceEventId(@Param("eventId") Long eventId);

    boolean existsByClusterId(Long clusterId);

    @EntityGraph(attributePaths = {
            "race", "race.event", "category", "cluster", "importBatch"
    })
    Optional<Registration> findById(Long id);

    @Query("select registration.race.event.id from Registration registration where registration.id = :registrationId")
    Optional<Long> findEventIdByRegistrationId(@Param("registrationId") Long registrationId);
}
