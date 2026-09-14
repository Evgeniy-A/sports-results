package ru.sportsresults.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.CreateEventSeriesRequest;
import ru.sportsresults.api.dto.EventSeriesDto;
import ru.sportsresults.api.dto.UpdateEventSeriesRequest;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.EventSeries;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.EventSeriesRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class EventSeriesService {
    private final EventSeriesRepository repository;
    private final AdminChangeLogRepository auditRepository;

    public EventSeriesService(EventSeriesRepository repository, AdminChangeLogRepository auditRepository) {
        this.repository = repository;
        this.auditRepository = auditRepository;
    }

    @Transactional(readOnly = true)
    public List<EventSeriesDto> listAll() {
        return repository.findAll().stream().map(EventSeriesService::toDto).toList();
    }

    @Transactional
    public EventSeriesDto create(CreateEventSeriesRequest request) {
        EventSeries series = new EventSeries();
        apply(series, request.name(), request.slug(), request.description(), request.active());
        try {
            return toDto(repository.saveAndFlush(series));
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("EVENT_SERIES_SLUG_EXISTS", "An event series with this slug already exists");
        }
    }

    @Transactional
    public EventSeriesDto update(Long id, UpdateEventSeriesRequest request, String actor) {
        EventSeries series = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_SERIES_NOT_FOUND", "Event series not found"));
        List<AdminChangeLog> changes = new ArrayList<>();
        change(changes, actor, id, "name", series.getName(), request.name().strip());
        change(changes, actor, id, "slug", series.getSlug(), request.slug().strip());
        change(changes, actor, id, "description", series.getDescription(), normalize(request.description()));
        change(changes, actor, id, "active", series.isActive(), request.active());
        apply(series, request.name(), request.slug(), request.description(), request.active());
        try {
            EventSeriesDto dto = toDto(repository.saveAndFlush(series));
            auditRepository.saveAll(changes);
            return dto;
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("EVENT_SERIES_SLUG_EXISTS", "An event series with this slug already exists");
        }
    }

    private static void apply(EventSeries series, String name, String slug, String description, boolean active) {
        series.setName(name.strip());
        series.setSlug(slug.strip());
        series.setDescription(normalize(description));
        series.setActive(active);
    }

    private static void change(List<AdminChangeLog> logs, String actor, Long id, String field, Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) return;
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.EVENT_SERIES);
        log.setEntityId(id);
        log.setFieldName(field);
        log.setOldValue(oldValue == null ? null : oldValue.toString());
        log.setNewValue(newValue == null ? null : newValue.toString());
        logs.add(log);
    }

    private static EventSeriesDto toDto(EventSeries series) {
        return new EventSeriesDto(
                series.getId(), series.getName(), series.getSlug(), series.getDescription(), series.isActive(),
                series.getCreatedAt(), series.getUpdatedAt()
        );
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
