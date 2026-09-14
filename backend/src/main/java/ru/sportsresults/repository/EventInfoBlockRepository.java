package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.EventInfoBlock;

import java.util.List;
import java.util.Optional;

public interface EventInfoBlockRepository extends JpaRepository<EventInfoBlock, Long> {
    List<EventInfoBlock> findAllByEventIdOrderByDisplayOrderAscIdAsc(Long eventId);
    Optional<EventInfoBlock> findByIdAndEventId(Long id, Long eventId);
}
