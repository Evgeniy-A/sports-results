package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.RaceResultPublicationHistory;

import java.util.List;

public interface RaceResultPublicationHistoryRepository extends JpaRepository<RaceResultPublicationHistory, Long> {
    List<RaceResultPublicationHistory> findAllByRaceIdOrderByCreatedAtAscIdAsc(Long raceId);
}
