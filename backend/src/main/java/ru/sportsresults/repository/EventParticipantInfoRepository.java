package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.EventParticipantInfo;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface EventParticipantInfoRepository extends JpaRepository<EventParticipantInfo, Long> {
    Optional<EventParticipantInfo> findByEventId(Long eventId);
    List<EventParticipantInfo> findAllByEventIdIn(Collection<Long> eventIds);
}
