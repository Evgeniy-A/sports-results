package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.EventScheduleItem;

import java.util.List;
import java.util.Optional;

public interface EventScheduleItemRepository extends JpaRepository<EventScheduleItem, Long> {
    List<EventScheduleItem> findAllByEventIdOrderByStartsAtAscDisplayOrderAscIdAsc(Long eventId);
    Optional<EventScheduleItem> findByIdAndEventId(Long id, Long eventId);
}
