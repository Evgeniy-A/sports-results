package ru.sportsresults.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.SportFormatDto;
import ru.sportsresults.api.dto.UpsertSportFormatRequest;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.SportFormat;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.SportFormatRepository;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

@Service
public class SportFormatService {
    public static final String DEFAULT_CODE = "default";
    public static final String COMPATIBILITY_DEFAULT_CODE = "race-only-default";
    public static final String DEFAULT_DISPLAY_NAME = "Основной формат";

    private final EventRepository eventRepository;
    private final SportFormatRepository formatRepository;
    private final RaceRepository raceRepository;
    private final AdminChangeLogRepository auditRepository;
    private final EventResultDataMutationGuard mutationGuard;

    public SportFormatService(EventRepository eventRepository, SportFormatRepository formatRepository,
                              RaceRepository raceRepository, AdminChangeLogRepository auditRepository,
                              EventResultDataMutationGuard mutationGuard) {
        this.eventRepository = eventRepository;
        this.formatRepository = formatRepository;
        this.raceRepository = raceRepository;
        this.auditRepository = auditRepository;
        this.mutationGuard = mutationGuard;
    }

    @Transactional(readOnly = true)
    public List<SportFormatDto> list(Long eventId) {
        requireEvent(eventId);
        return formatRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId).stream()
                .map(SportFormatService::toDto)
                .toList();
    }

    @Transactional
    public SportFormatDto create(Long eventId, UpsertSportFormatRequest request, String actor) {
        Event event = mutationGuard.lock(eventId);
        SportFormat format = new SportFormat();
        format.setEvent(event);
        apply(format, request);
        try {
            SportFormat saved = formatRepository.saveAndFlush(format);
            audit(actor, saved.getId(), "created", null, saved.getDisplayName());
            mutationGuard.bump(event);
            return toDto(saved);
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    @Transactional
    public SportFormatDto update(Long eventId, Long formatId, UpsertSportFormatRequest request, String actor) {
        Event event = mutationGuard.lock(eventId);
        SportFormat format = requireFormat(eventId, formatId);
        String oldCode = format.getCode();
        String oldSourceName = format.getSourceName();
        String oldDisplayName = format.getDisplayName();
        int oldDisplayOrder = format.getDisplayOrder();
        boolean oldPublicVisible = format.isPublicVisible();
        apply(format, request);
        try {
            SportFormat saved = formatRepository.saveAndFlush(format);
            List<AdminChangeLog> changes = new ArrayList<>();
            change(changes, actor, formatId, "code", oldCode, saved.getCode());
            change(changes, actor, formatId, "sourceName", oldSourceName, saved.getSourceName());
            change(changes, actor, formatId, "displayName", oldDisplayName, saved.getDisplayName());
            change(changes, actor, formatId, "displayOrder", oldDisplayOrder, saved.getDisplayOrder());
            change(changes, actor, formatId, "publicVisible", oldPublicVisible, saved.isPublicVisible());
            auditRepository.saveAll(changes);
            if (!changes.isEmpty()) {
                mutationGuard.bump(event);
            }
            return toDto(saved);
        } catch (DataIntegrityViolationException exception) {
            throw conflict();
        }
    }

    @Transactional
    public void delete(Long eventId, Long formatId, String actor) {
        Event event = mutationGuard.lock(eventId);
        SportFormat format = requireFormat(eventId, formatId);
        if (raceRepository.existsBySportFormatId(formatId)) {
            throw new RequestConflictException("SPORT_FORMAT_IN_USE", "Move or delete its races before deleting the sport format");
        }
        formatRepository.delete(format);
        formatRepository.flush();
        audit(actor, formatId, "deleted", format.getDisplayName(), null);
        mutationGuard.bump(event);
    }

    @Transactional
    public SportFormat ensureDefault(Event event) {
        List<SportFormat> existing = formatRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(event.getId());
        if (existing.size() == 1) {
            return existing.getFirst();
        }
        return formatRepository.findByEventIdAndCode(event.getId(), DEFAULT_CODE)
                .orElseGet(() -> createDefault(event, existing));
    }

    /**
     * Assigns Race-only API creates to an invisible implementation detail without
     * reusing an arbitrary single business format.
     */
    @Transactional
    public SportFormat ensureRaceOnlyDefault(Event event, String actor) {
        List<SportFormat> existing = formatRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(event.getId());
        SportFormat legacyDefault = formatRepository.findByEventIdAndCode(event.getId(), DEFAULT_CODE).orElse(null);
        if (legacyDefault != null
                && legacyDefault.isPublicVisible()
                && RacePresentation.isTechnicalFormatName(legacyDefault.getDisplayName())) {
            return legacyDefault;
        }

        SportFormat compatibilityDefault = formatRepository
                .findByEventIdAndCode(event.getId(), COMPATIBILITY_DEFAULT_CODE)
                .orElse(null);
        if (compatibilityDefault == null) {
            compatibilityDefault = new SportFormat();
            compatibilityDefault.setEvent(event);
            compatibilityDefault.setCode(COMPATIBILITY_DEFAULT_CODE);
            compatibilityDefault.setDisplayName(DEFAULT_DISPLAY_NAME);
            compatibilityDefault.setDisplayOrder(existing.stream()
                    .mapToInt(SportFormat::getDisplayOrder).max().orElse(-1) + 1);
            compatibilityDefault.setPublicVisible(true);
            SportFormat saved = formatRepository.saveAndFlush(compatibilityDefault);
            audit(actor, saved.getId(), "created", null, saved.getDisplayName());
            return saved;
        }
        if (!RacePresentation.isTechnicalFormatName(compatibilityDefault.getDisplayName())) {
            throw new RequestConflictException(
                    "COMPATIBILITY_FORMAT_INVALID",
                    "The reserved compatibility sport format has a non-technical display name"
            );
        }
        if (!compatibilityDefault.isPublicVisible()) {
            compatibilityDefault.setPublicVisible(true);
            formatRepository.saveAndFlush(compatibilityDefault);
            audit(actor, compatibilityDefault.getId(), "publicVisible", "false", "true");
        }
        return compatibilityDefault;
    }

    @Transactional
    public SportFormat resolveImportFormat(Event event, String code, String sourceName, String displayName) {
        String normalizedCode = normalize(code);
        String normalizedSourceName = normalize(sourceName);
        String normalizedDisplayName = normalize(displayName);
        if (normalizedCode == null && normalizedSourceName == null && normalizedDisplayName == null) {
            return ensureDefault(event);
        }
        SportFormat existing = normalizedCode == null
                ? null
                : formatRepository.findByEventIdAndCode(event.getId(), normalizedCode).orElse(null);
        if (existing == null && normalizedSourceName != null) {
            existing = formatRepository.findByEventIdAndSourceName(event.getId(), normalizedSourceName).orElse(null);
        }
        if (existing != null) {
            return existing;
        }
        SportFormat format = new SportFormat();
        format.setEvent(event);
        format.setCode(normalizedCode);
        format.setSourceName(normalizedSourceName);
        format.setDisplayName(firstNonNull(normalizedDisplayName, normalizedSourceName, normalizedCode));
        int order = formatRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(event.getId()).stream()
                .mapToInt(SportFormat::getDisplayOrder).max().orElse(-1) + 1;
        format.setDisplayOrder(order);
        return formatRepository.saveAndFlush(format);
    }

    @Transactional(readOnly = true)
    public SportFormat requireFormat(Long eventId, Long formatId) {
        return formatRepository.findByIdAndEventId(formatId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "SPORT_FORMAT_NOT_FOUND", "Sport format not found in this event"));
    }

    private SportFormat createDefault(Event event, List<SportFormat> existing) {
        SportFormat format = new SportFormat();
        format.setEvent(event);
        format.setCode(DEFAULT_CODE);
        format.setDisplayName(DEFAULT_DISPLAY_NAME);
        format.setDisplayOrder(existing.stream().mapToInt(SportFormat::getDisplayOrder).max().orElse(-1) + 1);
        return formatRepository.saveAndFlush(format);
    }

    private Event requireEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found"));
    }

    private static void apply(SportFormat format, UpsertSportFormatRequest request) {
        format.setCode(normalize(request.code()));
        format.setSourceName(normalize(request.sourceName()));
        format.setDisplayName(request.displayName().strip());
        format.setDisplayOrder(request.displayOrder());
        if (request.publicVisible() != null) {
            format.setPublicVisible(request.publicVisible());
        }
    }

    public static SportFormatDto toDto(SportFormat format) {
        return new SportFormatDto(format.getId(), format.getEvent().getId(), format.getCode(), format.getSourceName(),
                format.getDisplayName(), format.getDisplayOrder(), format.isPublicVisible());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) return value;
        }
        throw new InvalidRequestException("SPORT_FORMAT_IDENTITY_REQUIRED", "Explicit sport format needs a code or source name");
    }

    private static RequestConflictException conflict() {
        return new RequestConflictException("SPORT_FORMAT_EXISTS", "Sport format code or source name already exists in this event");
    }

    private void audit(String actor, Long id, String field, String oldValue, String newValue) {
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.SPORT_FORMAT);
        log.setEntityId(id);
        log.setFieldName(field);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        auditRepository.save(log);
    }

    private static void change(List<AdminChangeLog> changes, String actor, Long id, String field,
                               Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.SPORT_FORMAT);
        log.setEntityId(id);
        log.setFieldName(field);
        log.setOldValue(oldValue == null ? null : oldValue.toString());
        log.setNewValue(newValue == null ? null : newValue.toString());
        changes.add(log);
    }
}
