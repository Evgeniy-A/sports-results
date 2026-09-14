package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.Event;
import ru.sportsresults.repository.EventRepository;

@Service
public class EventResultDataMutationGuard {

    private final EventRepository eventRepository;

    public EventResultDataMutationGuard(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Event lock(Long eventId) {
        return eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long bump(Event event) {
        if (event.getResultDataRevision() == Long.MAX_VALUE) {
            throw new IllegalStateException("Event result data revision is exhausted");
        }
        long revision = event.getResultDataRevision() + 1;
        event.setResultDataRevision(revision);
        eventRepository.save(event);
        return revision;
    }
}
