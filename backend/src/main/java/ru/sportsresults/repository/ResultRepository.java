package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.Result;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResultRepository extends JpaRepository<Result, Long>, ResultSearchRepository {
    Optional<Result> findByRegistrationId(Long registrationId);

    List<Result> findAllByRegistrationIdIn(Collection<Long> registrationIds);

    boolean existsByRegistrationRaceId(Long raceId);

    @Override
    @EntityGraph(attributePaths = {
            "registration",
            "registration.race",
            "registration.race.event",
            "registration.category"
    })
    Optional<Result> findById(Long id);

    @Query("select count(result) from Result result "
            + "where result.registration.race.event.id = :eventId and result.registration.retiredAt is null")
    long countByRegistrationRaceEventId(@Param("eventId") Long eventId);

    @Query("select result.registration.race.event.id from Result result where result.id = :resultId")
    Optional<Long> findEventIdByResultId(@Param("resultId") Long resultId);
}
