package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.AwardPolicy;

import java.util.Optional;
import java.util.List;

public interface AwardPolicyRepository extends JpaRepository<AwardPolicy, Long> {
    @EntityGraph(attributePaths = {"race", "race.event"})
    Optional<AwardPolicy> findByRaceId(Long raceId);

    @EntityGraph(attributePaths = {"race", "race.event"})
    List<AwardPolicy> findAllByRaceEventId(Long eventId);
}
