package ru.sportsresults.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.CreateEventSeriesRequest;
import ru.sportsresults.api.dto.EventSeriesDto;
import ru.sportsresults.api.dto.ResultInquiryDefaultsDto;
import ru.sportsresults.api.dto.UpdateResultInquirySettingsRequest;
import ru.sportsresults.api.dto.UpdateEventSeriesRequest;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.EventSeries;
import ru.sportsresults.domain.ResultInquiryDeadlineMode;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.EventSeriesRepository;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.EventSeriesStartTemplateRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class EventSeriesService {
    private final EventSeriesRepository repository;
    private final EventRepository eventRepository;
    private final EventSeriesStartTemplateRepository startTemplateRepository;
    private final AdminChangeLogRepository auditRepository;
    private final SlugGenerator slugGenerator;

    public EventSeriesService(
            EventSeriesRepository repository,
            EventRepository eventRepository,
            EventSeriesStartTemplateRepository startTemplateRepository,
            AdminChangeLogRepository auditRepository,
            SlugGenerator slugGenerator
    ) {
        this.repository = repository;
        this.eventRepository = eventRepository;
        this.startTemplateRepository = startTemplateRepository;
        this.auditRepository = auditRepository;
        this.slugGenerator = slugGenerator;
    }

    @Transactional(readOnly = true)
    public List<EventSeriesDto> listAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    @Transactional
    public EventSeriesDto create(CreateEventSeriesRequest request) {
        EventSeries series = new EventSeries();
        String slug = slugGenerator.uniqueSlug(
                slugSource(request.slug(), request.name()), "series", repository::existsBySlug
        );
        apply(series, request.name(), slug, request.description(), request.active());
        applyDefaults(series, request.resultInquiryDefaults());
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
        String slug = request.slug() == null || request.slug().isBlank()
                ? series.getSlug() : request.slug().strip();
        List<AdminChangeLog> changes = new ArrayList<>();
        change(changes, actor, id, "name", series.getName(), request.name().strip());
        change(changes, actor, id, "slug", series.getSlug(), slug);
        change(changes, actor, id, "description", series.getDescription(), normalize(request.description()));
        change(changes, actor, id, "active", series.isActive(), request.active());
        addDefaultChanges(changes, actor, id, series, request.resultInquiryDefaults());
        apply(series, request.name(), slug, request.description(), request.active());
        applyDefaults(series, request.resultInquiryDefaults());
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

    private EventSeriesDto toDto(EventSeries series) {
        return new EventSeriesDto(
                series.getId(), series.getName(), series.getSlug(), series.getDescription(), series.isActive(),
                eventRepository.countByEventSeriesId(series.getId()),
                startTemplateRepository.countByEventSeriesId(series.getId()),
                new ResultInquiryDefaultsDto(
                        series.isDefaultResultInquiryEnabled(),
                        series.getDefaultResultInquiryDeadlineMode(),
                        series.getDefaultResultInquiryWindowDays(),
                        series.getDefaultResultInquiryFixedDate(),
                        series.getDefaultResultInquiryEmail()
                ),
                series.getCreatedAt(), series.getUpdatedAt()
        );
    }

    private static void applyDefaults(EventSeries series, UpdateResultInquirySettingsRequest request) {
        if (request == null) {
            return;
        }
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        ResultInquiryDeadlineMode mode = request.deadlineMode() == null
                ? ResultInquiryDeadlineMode.AFTER_EVENT_DAYS
                : request.deadlineMode();
        String email = normalize(request.email());
        EventService.validateResultInquirySettings(
                enabled, mode, request.windowDays(), request.fixedDate(), email
        );
        series.setDefaultResultInquiryEnabled(enabled);
        series.setDefaultResultInquiryDeadlineMode(mode);
        series.setDefaultResultInquiryWindowDays(request.windowDays());
        series.setDefaultResultInquiryFixedDate(request.fixedDate());
        series.setDefaultResultInquiryEmail(email);
    }

    private static void addDefaultChanges(
            List<AdminChangeLog> changes,
            String actor,
            Long id,
            EventSeries series,
            UpdateResultInquirySettingsRequest request
    ) {
        if (request == null) {
            return;
        }
        ResultInquiryDeadlineMode mode = request.deadlineMode() == null
                ? ResultInquiryDeadlineMode.AFTER_EVENT_DAYS
                : request.deadlineMode();
        change(changes, actor, id, "defaultResultInquiryEnabled",
                series.isDefaultResultInquiryEnabled(), Boolean.TRUE.equals(request.enabled()));
        change(changes, actor, id, "defaultResultInquiryDeadlineMode",
                series.getDefaultResultInquiryDeadlineMode(), mode);
        change(changes, actor, id, "defaultResultInquiryWindowDays",
                series.getDefaultResultInquiryWindowDays(), request.windowDays());
        change(changes, actor, id, "defaultResultInquiryFixedDate",
                series.getDefaultResultInquiryFixedDate(), request.fixedDate());
        change(changes, actor, id, "defaultResultInquiryEmail",
                series.getDefaultResultInquiryEmail(), normalize(request.email()));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String slugSource(String requestedSlug, String name) {
        return requestedSlug == null || requestedSlug.isBlank() ? name : requestedSlug.strip();
    }
}
