package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.Checkpoint;

import java.util.List;

public interface CheckpointRepository extends JpaRepository<Checkpoint, Long> {
    List<Checkpoint> findAllByRaceIdOrderBySequenceNumberAsc(Long raceId);
}
