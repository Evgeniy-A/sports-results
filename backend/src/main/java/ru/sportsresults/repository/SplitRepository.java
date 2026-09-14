package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import ru.sportsresults.domain.Split;

import java.util.List;

public interface SplitRepository extends JpaRepository<Split, Long> {
    @EntityGraph(attributePaths = "checkpoint")
    List<Split> findAllByResultIdOrderByCheckpointSequenceNumberAsc(Long resultId);
}
