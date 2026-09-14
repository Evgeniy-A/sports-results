package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.*;
import ru.sportsresults.domain.*;
import ru.sportsresults.repository.*;

import java.util.List;

@Service
public class EventContentService {
    private final EventService eventService;
    private final EventParticipantInfoRepository participantInfoRepository;
    private final EventInfoBlockRepository infoBlockRepository;
    private final EventScheduleItemRepository scheduleRepository;
    private final AdminChangeLogRepository auditRepository;

    public EventContentService(EventService eventService,
                               EventParticipantInfoRepository participantInfoRepository,
                               EventInfoBlockRepository infoBlockRepository,
                               EventScheduleItemRepository scheduleRepository,
                               AdminChangeLogRepository auditRepository) {
        this.eventService = eventService;
        this.participantInfoRepository = participantInfoRepository;
        this.infoBlockRepository = infoBlockRepository;
        this.scheduleRepository = scheduleRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional(readOnly = true)
    public EventParticipantInfoDto getParticipantInfo(Long eventId) {
        eventService.requireEvent(eventId);
        return participantInfoRepository.findByEventId(eventId).map(EventContentService::toDto).orElse(null);
    }

    @Transactional
    public EventParticipantInfoDto updateParticipantInfo(Long eventId, UpdateEventParticipantInfoRequest request, String actor) {
        Event event = eventService.requireEvent(eventId);
        EventParticipantInfo info = participantInfoRepository.findByEventId(eventId).orElseGet(() -> {
            EventParticipantInfo created = new EventParticipantInfo();
            created.setEvent(event);
            return created;
        });
        info.setShortDescription(normalize(request.shortDescription()));
        info.setVenueName(normalize(request.venueName()));
        info.setVenueAddress(normalize(request.venueAddress()));
        info.setLocationDescription(normalize(request.locationDescription()));
        info.setLatitude(request.latitude());
        info.setLongitude(request.longitude());
        info.setAdditionalInfo(normalize(request.additionalInfo()));
        EventParticipantInfo saved = participantInfoRepository.saveAndFlush(info);
        audit(actor, AuditEntityType.EVENT_PARTICIPANT_INFO, saved.getId(), "updated", null, toDto(saved).toString());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<EventInfoBlockDto> listInfoBlocks(Long eventId) {
        eventService.requireEvent(eventId);
        return infoBlockRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId).stream()
                .map(EventContentService::toDto).toList();
    }

    @Transactional
    public EventInfoBlockDto createInfoBlock(Long eventId, UpsertEventInfoBlockRequest request, String actor) {
        EventInfoBlock block = new EventInfoBlock();
        block.setEvent(eventService.requireEvent(eventId));
        apply(block, request);
        EventInfoBlock saved = infoBlockRepository.saveAndFlush(block);
        audit(actor, AuditEntityType.EVENT_INFO_BLOCK, saved.getId(), "created", null, toDto(saved).toString());
        return toDto(saved);
    }

    @Transactional
    public EventInfoBlockDto updateInfoBlock(Long eventId, Long blockId, UpsertEventInfoBlockRequest request, String actor) {
        EventInfoBlock block = infoBlockRepository.findByIdAndEventId(blockId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_INFO_BLOCK_NOT_FOUND", "Event info block not found"));
        String old = toDto(block).toString();
        apply(block, request);
        EventInfoBlock saved = infoBlockRepository.save(block);
        audit(actor, AuditEntityType.EVENT_INFO_BLOCK, blockId, "updated", old, toDto(saved).toString());
        return toDto(saved);
    }

    @Transactional
    public void deleteInfoBlock(Long eventId, Long blockId, String actor) {
        EventInfoBlock block = infoBlockRepository.findByIdAndEventId(blockId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_INFO_BLOCK_NOT_FOUND", "Event info block not found"));
        infoBlockRepository.delete(block);
        audit(actor, AuditEntityType.EVENT_INFO_BLOCK, blockId, "deleted", toDto(block).toString(), null);
    }

    @Transactional(readOnly = true)
    public List<EventScheduleItemDto> listSchedule(Long eventId) {
        eventService.requireEvent(eventId);
        return scheduleRepository.findAllByEventIdOrderByStartsAtAscDisplayOrderAscIdAsc(eventId).stream()
                .map(EventContentService::toDto).toList();
    }

    @Transactional
    public EventScheduleItemDto createScheduleItem(Long eventId, UpsertEventScheduleItemRequest request, String actor) {
        validateSchedule(request);
        EventScheduleItem item = new EventScheduleItem();
        item.setEvent(eventService.requireEvent(eventId));
        apply(item, request);
        EventScheduleItem saved = scheduleRepository.saveAndFlush(item);
        audit(actor, AuditEntityType.EVENT_SCHEDULE_ITEM, saved.getId(), "created", null, toDto(saved).toString());
        return toDto(saved);
    }

    @Transactional
    public EventScheduleItemDto updateScheduleItem(Long eventId, Long itemId, UpsertEventScheduleItemRequest request, String actor) {
        validateSchedule(request);
        EventScheduleItem item = scheduleRepository.findByIdAndEventId(itemId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_SCHEDULE_ITEM_NOT_FOUND", "Event schedule item not found"));
        String old = toDto(item).toString();
        apply(item, request);
        EventScheduleItem saved = scheduleRepository.save(item);
        audit(actor, AuditEntityType.EVENT_SCHEDULE_ITEM, itemId, "updated", old, toDto(saved).toString());
        return toDto(saved);
    }

    @Transactional
    public void deleteScheduleItem(Long eventId, Long itemId, String actor) {
        EventScheduleItem item = scheduleRepository.findByIdAndEventId(itemId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_SCHEDULE_ITEM_NOT_FOUND", "Event schedule item not found"));
        scheduleRepository.delete(item);
        audit(actor, AuditEntityType.EVENT_SCHEDULE_ITEM, itemId, "deleted", toDto(item).toString(), null);
    }

    private static void validateSchedule(UpsertEventScheduleItemRequest request) {
        if (request.endsAt() != null && request.endsAt().isBefore(request.startsAt())) {
            throw new InvalidRequestException("INVALID_SCHEDULE_DATES", "endsAt must not be before startsAt");
        }
    }

    private static void apply(EventInfoBlock block, UpsertEventInfoBlockRequest request) {
        block.setTitle(request.title().strip());
        block.setContent(request.content().strip());
        block.setDisplayOrder(request.displayOrder());
    }

    private static void apply(EventScheduleItem item, UpsertEventScheduleItemRequest request) {
        item.setStartsAt(request.startsAt());
        item.setEndsAt(request.endsAt());
        item.setTitle(request.title().strip());
        item.setDescription(normalize(request.description()));
        item.setDisplayOrder(request.displayOrder());
    }

    public static EventParticipantInfoDto toDto(EventParticipantInfo info) {
        return new EventParticipantInfoDto(info.getId(), info.getShortDescription(), info.getVenueName(),
                info.getVenueAddress(), info.getLocationDescription(), info.getLatitude(), info.getLongitude(),
                info.getAdditionalInfo());
    }

    public static EventInfoBlockDto toDto(EventInfoBlock block) {
        return new EventInfoBlockDto(block.getId(), block.getTitle(), block.getContent(), block.getDisplayOrder());
    }

    public static EventScheduleItemDto toDto(EventScheduleItem item) {
        return new EventScheduleItemDto(item.getId(), item.getStartsAt(), item.getEndsAt(), item.getTitle(),
                item.getDescription(), item.getDisplayOrder());
    }

    private void audit(String actor, AuditEntityType type, Long entityId, String operation, String oldValue, String newValue) {
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(type);
        log.setEntityId(entityId);
        log.setFieldName(operation);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        auditRepository.save(log);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
