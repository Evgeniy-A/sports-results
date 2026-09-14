package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.StartCluster;

import java.util.List;
import java.util.Optional;

public interface StartClusterRepository extends JpaRepository<StartCluster, Long> {
    List<StartCluster> findAllByRaceIdOrderByDisplayOrderAscIdAsc(Long raceId);
    List<StartCluster> findAllByRaceEventIdOrderByRaceDisplayOrderAscDisplayOrderAscIdAsc(Long eventId);
    Optional<StartCluster> findByIdAndRaceId(Long id, Long raceId);
    Optional<StartCluster> findByRaceIdAndCode(Long raceId, String code);
    Optional<StartCluster> findByRaceIdAndSourceName(Long raceId, String sourceName);
    long countByRaceId(Long raceId);
}
