package ru.sportsresults.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.CategoryOptionDto;
import ru.sportsresults.api.dto.BulkCreateEventsRequest;
import ru.sportsresults.api.dto.BulkCreateEventsResponseDto;
import ru.sportsresults.api.dto.BulkEventItemRequest;
import ru.sportsresults.api.dto.BulkEventPreviewDto;
import ru.sportsresults.api.dto.CreateEventRequest;
import ru.sportsresults.api.dto.EventDto;
import ru.sportsresults.api.dto.EventFilterOptionsDto;
import ru.sportsresults.api.dto.EventStartDefinitionRequest;
import ru.sportsresults.api.dto.EventStartPreviewDto;
import ru.sportsresults.api.dto.EventSeriesOptionDto;
import ru.sportsresults.api.dto.EventWithStartsDto;
import ru.sportsresults.api.dto.CreateEventWithStartsRequest;
import ru.sportsresults.api.dto.PageResponse;
import ru.sportsresults.api.dto.RaceDto;
import ru.sportsresults.api.dto.UpdateEventRequest;
import ru.sportsresults.api.dto.UpdateEventPublicationRequest;
import ru.sportsresults.api.dto.UpdateAwardPolicyRequest;
import ru.sportsresults.api.dto.ResultInquirySettingsDto;
import ru.sportsresults.api.dto.UpdateResultInquirySettingsRequest;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.AwardPolicy;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.EventPublicationStatus;
import ru.sportsresults.domain.EventParticipantInfo;
import ru.sportsresults.domain.EventPhase;
import ru.sportsresults.domain.PublicResultVisibility;
import ru.sportsresults.domain.ResultsPublicationStatus;
import ru.sportsresults.domain.EventSeries;
import ru.sportsresults.domain.EventSeriesStartAwardPolicyTemplate;
import ru.sportsresults.domain.EventSeriesStartCategoryTemplate;
import ru.sportsresults.domain.EventSeriesStartTemplate;
import ru.sportsresults.domain.Race;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.AwardPolicyRepository;
import ru.sportsresults.repository.CategoryRepository;
import ru.sportsresults.repository.EventCatalogCriteria;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.EventSeriesRepository;
import ru.sportsresults.repository.EventSeriesStartAwardPolicyTemplateRepository;
import ru.sportsresults.repository.EventSeriesStartCategoryTemplateRepository;
import ru.sportsresults.repository.EventSeriesStartTemplateRepository;
import ru.sportsresults.repository.EventParticipantInfoRepository;
import ru.sportsresults.repository.RaceRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EventService {

    private static final int MAX_CATALOG_PAGE_SIZE = 100;
    private static final java.util.regex.Pattern EMAIL_PATTERN = java.util.regex.Pattern.compile(
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"
    );

    private final EventRepository eventRepository;
    private final EventSeriesRepository eventSeriesRepository;
    private final EventSeriesStartTemplateRepository startTemplateRepository;
    private final EventSeriesStartAwardPolicyTemplateRepository templatePolicyRepository;
    private final EventSeriesStartCategoryTemplateRepository templateCategoryRepository;
    private final RaceRepository raceRepository;
    private final CategoryRepository categoryRepository;
    private final AwardPolicyRepository awardPolicyRepository;
    private final AdminChangeLogRepository changeLogRepository;
    private final PublicCategoryPresentation categoryPresentation;
    private final ResultConfigurationChangeGuard configurationGuard;
    private final EventParticipantInfoRepository participantInfoRepository;
    private final EventResultDataMutationGuard mutationGuard;
    private final SlugGenerator slugGenerator;

    public EventService(
            EventRepository eventRepository,
            EventSeriesRepository eventSeriesRepository,
            EventSeriesStartTemplateRepository startTemplateRepository,
            EventSeriesStartAwardPolicyTemplateRepository templatePolicyRepository,
            EventSeriesStartCategoryTemplateRepository templateCategoryRepository,
            RaceRepository raceRepository,
            CategoryRepository categoryRepository,
            AwardPolicyRepository awardPolicyRepository,
            AdminChangeLogRepository changeLogRepository,
            PublicCategoryPresentation categoryPresentation,
            ResultConfigurationChangeGuard configurationGuard,
            EventParticipantInfoRepository participantInfoRepository,
            EventResultDataMutationGuard mutationGuard,
            SlugGenerator slugGenerator
    ) {
        this.eventRepository = eventRepository;
        this.eventSeriesRepository = eventSeriesRepository;
        this.startTemplateRepository = startTemplateRepository;
        this.templatePolicyRepository = templatePolicyRepository;
        this.templateCategoryRepository = templateCategoryRepository;
        this.raceRepository = raceRepository;
        this.categoryRepository = categoryRepository;
        this.awardPolicyRepository = awardPolicyRepository;
        this.changeLogRepository = changeLogRepository;
        this.categoryPresentation = categoryPresentation;
        this.configurationGuard = configurationGuard;
        this.participantInfoRepository = participantInfoRepository;
        this.mutationGuard = mutationGuard;
        this.slugGenerator = slugGenerator;
    }

    @Transactional(readOnly = true)
    public PageResponse<EventDto> searchPublishedEvents(
            Integer year,
            Long eventSeriesId,
            String city,
            LocalDate date,
            EventPhase phase,
            int page,
            int size
    ) {
        validatePage(page, size);
        Page<Event> result = eventRepository.searchPublished(
                new EventCatalogCriteria(year, eventSeriesId, normalizeNullable(city), date, phase),
                page,
                size
        );
        Map<Long, EventParticipantInfo> infoByEvent = participantInfoRepository.findAllByEventIdIn(
                        result.getContent().stream().map(Event::getId).toList())
                .stream().collect(Collectors.toMap(info -> info.getEvent().getId(), info -> info));
        return new PageResponse<>(
                result.getContent().stream().map(event -> toPublicDto(event, infoByEvent.get(event.getId()))).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages(),
                "startsAt",
                "desc"
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<EventDto> searchAdminEvents(
            String name,
            String location,
            EventPublicationStatus publicationStatus,
            ResultsPublicationStatus resultsPublicationStatus,
            int page,
            int size
    ) {
        validatePage(page, size);
        Page<Event> result = eventRepository.searchAdmin(
                normalizeNullable(name),
                normalizeNullable(location),
                publicationStatus,
                resultsPublicationStatus,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("startsAt"), Sort.Order.desc("id")))
        );
        Map<Long, EventParticipantInfo> infoByEvent = participantInfoRepository.findAllByEventIdIn(
                        result.getContent().stream().map(Event::getId).toList())
                .stream().collect(Collectors.toMap(info -> info.getEvent().getId(), info -> info));
        return new PageResponse<>(
                result.getContent().stream().map(event -> toDto(event, infoByEvent.get(event.getId()))).toList(),
                page,
                size,
                result.getTotalElements(),
                result.getTotalPages(),
                "startsAt",
                "desc"
        );
    }

    @Transactional(readOnly = true)
    public EventDto getAdminEvent(Long eventId) {
        Event event = requireEvent(eventId);
        return toDto(event, participantInfoRepository.findByEventId(eventId).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<RaceDto> listAdminEventRaces(Long eventId) {
        requireEvent(eventId);
        Map<Long, AwardPolicy> policiesByRace = awardPolicyRepository.findAllByRaceEventId(eventId).stream()
                .collect(Collectors.toMap(policy -> policy.getRace().getId(), policy -> policy));
        return raceRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId).stream()
                .map(race -> toDto(
                        race,
                        policiesByRace.containsKey(race.getId())
                                && policiesByRace.get(race.getId()).isCategoryEnabled()
                ))
                .toList();
    }

    public PageResponse<EventDto> searchPublishedEvents(
            Integer year, Long eventSeriesId, String city, LocalDate date, int page, int size
    ) {
        return searchPublishedEvents(year, eventSeriesId, city, date, null, page, size);
    }

    @Transactional(readOnly = true)
    public EventFilterOptionsDto filterOptions(Integer year, Long eventSeriesId, String city, LocalDate date) {
        List<Event> events = eventRepository.findAllByPublicationStatusAndEventSeriesActiveTrue(EventPublicationStatus.PUBLISHED);
        String normalizedCity = normalizeNullable(city);

        List<Integer> years = events.stream()
                .filter(event -> matches(event, null, eventSeriesId, normalizedCity, date))
                .filter(event -> event.getStartsAt() != null)
                .map(event -> event.getStartsAt().atZone(ZoneId.of(event.getTimeZone())).getYear())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
        List<EventSeriesOptionDto> series = events.stream()
                .filter(event -> matches(event, year, null, normalizedCity, date))
                .map(Event::getEventSeries)
                .collect(java.util.stream.Collectors.toMap(
                        EventSeries::getId,
                        value -> new EventSeriesOptionDto(value.getId(), value.getName()),
                        (left, right) -> left
                ))
                .values().stream()
                .sorted(Comparator.comparing(EventSeriesOptionDto::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<String> cities = events.stream()
                .filter(event -> matches(event, year, eventSeriesId, null, date))
                .map(Event::getLocation)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        return new EventFilterOptionsDto(years, series, cities);
    }

    @Transactional(readOnly = true)
    public EventDto getPublishedEvent(Long eventId) {
        Event event = requirePublishedEvent(eventId);
        return toPublicDto(event, participantInfoRepository.findByEventId(eventId).orElse(null));
    }

    @Transactional(readOnly = true)
    public EventDto getPublishedEventBySlug(String slug) {
        return eventRepository.findBySlugAndPublicationStatus(slug, EventPublicationStatus.PUBLISHED)
                .map(event -> toPublicDto(event, participantInfoRepository.findByEventId(event.getId()).orElse(null)))
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Published event not found"));
    }

    @Transactional(readOnly = true)
    public List<RaceDto> listPublishedEventRaces(Long eventId) {
        requirePublishedEvent(eventId);
        Map<Long, AwardPolicy> policiesByRace = awardPolicyRepository.findAllByRaceEventId(eventId).stream()
                .collect(Collectors.toMap(policy -> policy.getRace().getId(), policy -> policy));
        return raceRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId).stream()
                .filter(Race::isPublicVisible)
                .map(race -> toDto(
                        race,
                        policiesByRace.containsKey(race.getId())
                                && policiesByRace.get(race.getId()).isCategoryEnabled()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryOptionDto> listPublishedEventCategories(Long eventId, Long raceId) {
        requirePublishedEvent(eventId);
        if (raceId == null) {
            return List.of();
        }
        Race race = raceRepository.findByIdAndEventId(raceId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found in this event"));
        if (!PublicResultVisibility.isRaceResultsPublic(race)) {
            throw new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found in this event");
        }
        boolean categoryStandingEnabled = awardPolicyRepository.findByRaceId(raceId)
                .map(AwardPolicy::isCategoryEnabled)
                .orElse(false);
        if (!categoryStandingEnabled) {
            return List.of();
        }
        return categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(raceId).stream()
                .filter(ru.sportsresults.domain.Category::isEnabled)
                .map(category -> new CategoryOptionDto(
                        category.getId(), raceId, race.getName(), categoryPresentation.publicName(category)
                ))
                .toList();
    }

    @Transactional
    public EventDto createEvent(CreateEventRequest request) {
        try {
            return toDto(createEventRecord(requireSeries(request.eventSeriesId()), request));
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("EVENT_SLUG_EXISTS", "An event with this slug already exists");
        }
    }

    @Transactional
    public EventWithStartsDto createEventWithStarts(CreateEventWithStartsRequest request) {
        EventSeries series = eventSeriesRepository.findByIdForUpdate(request.event().eventSeriesId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EVENT_SERIES_NOT_FOUND", "Event series not found"
                ));
        List<ResolvedStartDefinition> starts = resolveStartDefinitions(series.getId(), request.starts());
        try {
            Event event = createEventRecord(series, request.event());
            return new EventWithStartsDto(toDto(event), materializeStarts(event, starts));
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException(
                    "EVENT_CREATION_CONFLICT", "Event or start source code conflicts with existing data"
            );
        }
    }

    @Transactional(readOnly = true)
    public BulkEventPreviewDto previewBulkEvents(BulkCreateEventsRequest request) {
        EventSeries series = requireSeries(request.eventSeriesId());
        List<ResolvedStartDefinition> starts = resolveStartDefinitions(series.getId(), request.starts());
        return toBulkPreview(series, prepareBulk(request), starts);
    }

    @Transactional
    public BulkCreateEventsResponseDto createBulkEvents(BulkCreateEventsRequest request) {
        EventSeries series = eventSeriesRepository.findByIdForUpdate(request.eventSeriesId())
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_SERIES_NOT_FOUND", "Event series not found"));
        BulkPreparation preparation = prepareBulk(request);
        List<ResolvedStartDefinition> starts = resolveStartDefinitions(series.getId(), request.starts());
        if (preparation.candidates().stream().anyMatch(candidate -> !candidate.creatable())) {
            throw new RequestConflictException(
                    "BULK_EVENT_DUPLICATE",
                    "One or more events already exist or repeat a location in this request"
            );
        }

        List<EventDto> created = new ArrayList<>();
        try {
            for (BulkCandidate candidate : preparation.candidates()) {
                CreateEventRequest eventRequest = new CreateEventRequest(
                        series.getId(), candidate.name(), null, candidate.startsAt(), null,
                        candidate.location(), candidate.timeZone(), EventPublicationStatus.DRAFT
                );
                Event event = createEventRecord(series, eventRequest);
                materializeStarts(event, starts);
                created.add(toDto(event));
            }
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("BULK_EVENT_CONFLICT", "Bulk event creation conflicted with existing data");
        }
        return new BulkCreateEventsResponseDto(created, created.size() * starts.size());
    }

    private BulkPreparation prepareBulk(BulkCreateEventsRequest request) {
        List<Event> existing = eventRepository.findAllByEventSeriesIdOrderByIdAsc(request.eventSeriesId());
        Map<String, Long> existingByKey = new HashMap<>();
        for (Event event : existing) {
            if (event.getStartsAt() == null || event.getLocation() == null) continue;
            LocalDate date = event.getStartsAt().atZone(ZoneId.of(event.getTimeZone())).toLocalDate();
            existingByKey.putIfAbsent(bulkDuplicateKey(event.getLocation(), date), event.getId());
        }

        Map<String, Integer> requestCounts = new HashMap<>();
        for (BulkEventItemRequest item : request.events()) {
            String key = bulkDuplicateKey(item.location(), request.date());
            requestCounts.merge(key, 1, Integer::sum);
        }

        List<BulkCandidate> candidates = request.events().stream().map(item -> {
            String name = item.name().strip();
            String location = item.location().strip();
            String timeZone = normalizedTimeZone(item.timeZone());
            String key = bulkDuplicateKey(location, request.date());
            boolean duplicateInRequest = requestCounts.getOrDefault(key, 0) > 1;
            Long existingEventId = existingByKey.get(key);
            Instant startsAt = request.date().atTime(LocalTime.MIDNIGHT).atZone(ZoneId.of(timeZone)).toInstant();
            return new BulkCandidate(name, location, timeZone, startsAt,
                    !duplicateInRequest && existingEventId == null, duplicateInRequest, existingEventId);
        }).toList();
        return new BulkPreparation(request.date(), candidates);
    }

    private static BulkEventPreviewDto toBulkPreview(
            EventSeries series,
            BulkPreparation preparation,
            List<ResolvedStartDefinition> starts
    ) {
        List<BulkEventPreviewDto.Item> items = preparation.candidates().stream()
                .map(candidate -> new BulkEventPreviewDto.Item(
                        candidate.name(), candidate.location(), candidate.timeZone(), candidate.creatable(),
                        candidate.duplicateInRequest(), candidate.existingEventId()
                ))
                .toList();
        return new BulkEventPreviewDto(
                series.getId(), series.getName(), preparation.date(), items.size(),
                (int) items.stream().filter(BulkEventPreviewDto.Item::creatable).count(),
                starts.size(), items.size() * starts.size(),
                starts.stream().map(start -> new EventStartPreviewDto(
                        start.name(), start.distanceMeters(), start.sourceCode(), start.publicVisible(),
                        start.awardPolicy() != null
                )).toList(),
                items
        );
    }

    private Event createEventRecord(EventSeries series, CreateEventRequest request) {
        validateDates(request.startsAt(), request.endsAt());
        EventPublicationStatus status = request.publicationStatus() == null
                ? EventPublicationStatus.DRAFT : request.publicationStatus();
        if (status == EventPublicationStatus.PUBLISHED && request.startsAt() == null) {
            throw new InvalidRequestException("EVENT_DATE_REQUIRED", "A published event must have a start date");
        }
        Event event = new Event();
        event.setEventSeries(series);
        String slug = slugGenerator.uniqueSlug(
                slugSource(request.slug(), request.name()), "event", eventRepository::existsBySlug
        );
        apply(event, request.name(), slug, request.startsAt(), request.endsAt(), request.location(),
                request.timeZone(), status);
        return eventRepository.saveAndFlush(event);
    }

    private List<RaceDto> materializeStarts(Event event, List<ResolvedStartDefinition> definitions) {
        List<RaceDto> created = new ArrayList<>();
        for (int index = 0; index < definitions.size(); index++) {
            ResolvedStartDefinition definition = definitions.get(index);
            Race race = new Race();
            race.setEvent(event);
            race.setName(definition.name());
            race.setSourceCode(definition.sourceCode());
            race.setDistanceMeters(definition.distanceMeters());
            race.setDisplayOrder(index);
            race.setPublicVisible(definition.publicVisible());
            race.setSlug(slugGenerator.uniqueSlug(
                    definition.name(), "start", candidate -> raceRepository.existsByEventIdAndSlug(event.getId(), candidate)
            ));
            UpdateAwardPolicyRequest configured = definition.awardPolicy();
            AwardPolicy policy = configured == null
                    ? AwardPolicyService.createDefault(race)
                    : AwardPolicyService.create(race, configured);
            race.setPublicRankingBasis(policy.getRankingBasis());
            raceRepository.saveAndFlush(race);
            awardPolicyRepository.saveAndFlush(policy);
            for (EventSeriesStartCategoryTemplate templateCategory : definition.categories()) {
                Category category = new Category();
                category.setRace(race);
                category.setSourceName(templateCategory.getSourceName());
                category.setDisplayName(templateCategory.getDisplayName());
                category.setMinAge(templateCategory.getMinAge());
                category.setMaxAge(templateCategory.getMaxAge());
                category.setGender(templateCategory.getGender());
                category.setDisplayOrder(templateCategory.getDisplayOrder());
                category.setEnabled(templateCategory.isEnabled());
                categoryRepository.save(category);
            }
            created.add(toDto(race, policy.isCategoryEnabled()));
        }
        return created;
    }

    private List<ResolvedStartDefinition> resolveStartDefinitions(
            Long eventSeriesId,
            List<EventStartDefinitionRequest> requests
    ) {
        List<EventStartDefinitionRequest> safeRequests = requests == null ? List.of() : requests;
        Map<Long, EventSeriesStartTemplate> templates = startTemplateRepository
                .findAllByEventSeriesIdOrderByDisplayOrderAscIdAsc(eventSeriesId).stream()
                .collect(Collectors.toMap(EventSeriesStartTemplate::getId, value -> value));
        Map<Long, EventSeriesStartAwardPolicyTemplate> policies = templatePolicyRepository
                .findAllByTemplateStartEventSeriesId(eventSeriesId).stream()
                .collect(Collectors.toMap(policy -> policy.getTemplateStart().getId(), value -> value));
        Map<Long, List<EventSeriesStartCategoryTemplate>> categories = templateCategoryRepository
                .findAllByEventSeriesId(eventSeriesId).stream()
                .collect(Collectors.groupingBy(
                        category -> category.getTemplateStart().getId(),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));
        Set<Long> usedTemplateIds = new HashSet<>();
        Set<String> usedSourceCodes = new HashSet<>();
        List<ResolvedStartDefinition> resolved = new ArrayList<>();
        for (int index = 0; index < safeRequests.size(); index++) {
            EventStartDefinitionRequest request = safeRequests.get(index);
            if (request.templateStartId() != null) {
                if (!templates.containsKey(request.templateStartId())) {
                    throw new InvalidRequestException(
                            "TEMPLATE_START_NOT_IN_SERIES", "Selected start does not belong to this template"
                    );
                }
                if (!usedTemplateIds.add(request.templateStartId())) {
                    throw new InvalidRequestException(
                            "DUPLICATE_TEMPLATE_START", "A template start can only be selected once"
                    );
                }
            }
            String sourceCode = normalizeStartSourceCode(request.sourceCode());
            if (sourceCode == null) {
                sourceCode = request.templateStartId() == null
                        ? "start-" + (index + 1)
                        : "template-start-" + request.templateStartId();
            }
            if (!usedSourceCodes.add(sourceCode.toLowerCase(Locale.ROOT))) {
                throw new RequestConflictException(
                        "RACE_SOURCE_CODE_DUPLICATE", "Start source codes must be unique inside an event"
                );
            }
            UpdateAwardPolicyRequest awardPolicy = request.awardPolicy();
            if (awardPolicy == null && request.templateStartId() != null) {
                EventSeriesStartAwardPolicyTemplate templatePolicy = policies.get(request.templateStartId());
                awardPolicy = templatePolicy == null ? null : toAwardPolicyRequest(templatePolicy);
            }
            if (awardPolicy != null) {
                AwardPolicyService.validate(awardPolicy);
            }
            resolved.add(new ResolvedStartDefinition(
                    request.name().strip(), request.distanceMeters(), sourceCode,
                    request.publicVisible() == null || request.publicVisible(), awardPolicy,
                    request.templateStartId() == null
                            ? List.of()
                            : List.copyOf(categories.getOrDefault(request.templateStartId(), List.of()))
            ));
        }
        return List.copyOf(resolved);
    }

    private static UpdateAwardPolicyRequest toAwardPolicyRequest(
            EventSeriesStartAwardPolicyTemplate policy
    ) {
        return new UpdateAwardPolicyRequest(
                policy.getRankingBasis(), policy.getPrimaryStandingMode(), policy.getAbsolutePrizePlaces(),
                policy.isCategoryEnabled(), policy.getAgeCalculationMode(), policy.getCategoryPrizePlaces(),
                policy.isExcludeAbsoluteWinnersFromCategory()
        );
    }

    private static String normalizeStartSourceCode(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String bulkDuplicateKey(String location, LocalDate date) {
        String normalizedLocation = location.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)
                .replace('ё', 'е');
        return normalizedLocation + "\u0000" + date;
    }

    private record BulkCandidate(
            String name,
            String location,
            String timeZone,
            Instant startsAt,
            boolean creatable,
            boolean duplicateInRequest,
            Long existingEventId
    ) {
    }

    private record BulkPreparation(LocalDate date, List<BulkCandidate> candidates) {
    }

    private record ResolvedStartDefinition(
            String name,
            java.math.BigDecimal distanceMeters,
            String sourceCode,
            boolean publicVisible,
            UpdateAwardPolicyRequest awardPolicy,
            List<EventSeriesStartCategoryTemplate> categories
    ) {
    }

    @Transactional
    public EventDto updateEvent(Long eventId, UpdateEventRequest request, String actor) {
        validateDates(request.startsAt(), request.endsAt());
        if (request.publicationStatus() == EventPublicationStatus.PUBLISHED && request.startsAt() == null) {
            throw new InvalidRequestException("EVENT_DATE_REQUIRED", "A published event must have a start date");
        }
        Event event = mutationGuard.lock(eventId);
        boolean ageReferenceChanged = !Objects.equals(event.getStartsAt(), request.startsAt())
                || !Objects.equals(event.getTimeZone(), normalizedTimeZone(request.timeZone()));
        List<Race> ageAffectedRaces = ageReferenceChanged
                ? configurationGuard.currentDataRaces(
                        raceRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId)
                )
                : List.of();
        if (ageReferenceChanged) {
            configurationGuard.requireDraft(ageAffectedRaces);
        }
        EventSeries series = requireSeries(request.eventSeriesId());
        String slug = request.slug() == null || request.slug().isBlank()
                ? event.getSlug() : request.slug().strip();
        List<AdminChangeLog> changes = new ArrayList<>();
        addChange(changes, actor, eventId, "eventSeriesId", event.getEventSeries().getId(), series.getId());
        addChange(changes, actor, eventId, "name", event.getName(), request.name().strip());
        addChange(changes, actor, eventId, "slug", event.getSlug(), slug);
        addChange(changes, actor, eventId, "startsAt", event.getStartsAt(), request.startsAt());
        addChange(changes, actor, eventId, "endsAt", event.getEndsAt(), request.endsAt());
        addChange(changes, actor, eventId, "location", event.getLocation(), normalizeNullable(request.location()));
        addChange(changes, actor, eventId, "timeZone", event.getTimeZone(), normalizedTimeZone(request.timeZone()));
        addChange(changes, actor, eventId, "publicationStatus", event.getPublicationStatus(), request.publicationStatus());
        event.setEventSeries(series);
        apply(event, request.name(), slug, request.startsAt(), request.endsAt(), request.location(),
                request.timeZone(), request.publicationStatus());
        try {
            EventDto dto = toDto(eventRepository.saveAndFlush(event));
            if (ageReferenceChanged) {
                configurationGuard.markRecalculationRequiredForCurrentData(ageAffectedRaces);
                mutationGuard.bump(event);
            }
            changeLogRepository.saveAll(changes);
            return dto;
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("EVENT_SLUG_EXISTS", "An event with this slug already exists");
        }
    }

    @Transactional(readOnly = true)
    public ResultInquirySettingsDto getResultInquirySettings(Long eventId) {
        return inquirySettings(requireEvent(eventId));
    }

    @Transactional
    public ResultInquirySettingsDto updateResultInquirySettings(
            Long eventId,
            UpdateResultInquirySettingsRequest request,
            String actor
    ) {
        Event event = requireEvent(eventId);
        boolean enabled = Boolean.TRUE.equals(request.enabled());
        Integer windowDays = request.windowDays();
        String email = normalizeNullable(request.email());
        validateResultInquirySettings(enabled, windowDays, email);

        List<AdminChangeLog> changes = new ArrayList<>();
        addChange(changes, actor, eventId, "resultInquiryEnabled", event.isResultInquiryEnabled(), enabled);
        addChange(changes, actor, eventId, "resultInquiryWindowDays", event.getResultInquiryWindowDays(), windowDays);
        addChange(changes, actor, eventId, "resultInquiryEmail", event.getResultInquiryEmail(), email);
        event.setResultInquiryEnabled(enabled);
        event.setResultInquiryWindowDays(windowDays);
        event.setResultInquiryEmail(email);
        Event saved = eventRepository.saveAndFlush(event);
        changeLogRepository.saveAll(changes);
        return inquirySettings(saved);
    }

    @Transactional
    public EventDto updatePublication(Long eventId, UpdateEventPublicationRequest request, String actor) {
        Event event = requireEvent(eventId);
        if (request.eventPublicationStatus() == EventPublicationStatus.PUBLISHED && event.getStartsAt() == null) {
            throw new InvalidRequestException("EVENT_DATE_REQUIRED", "A published event must have a start date");
        }
        if (request.resultsPublicationStatus() == ResultsPublicationStatus.PUBLISHED
                && request.eventPublicationStatus() != EventPublicationStatus.PUBLISHED) {
            throw new InvalidRequestException("EVENT_MUST_BE_PUBLISHED", "Publish the event before publishing results");
        }
        List<AdminChangeLog> changes = new ArrayList<>();
        addChange(changes, actor, eventId, "publicationStatus", event.getPublicationStatus(), request.eventPublicationStatus());
        addChange(changes, actor, eventId, "resultsPublicationStatus", event.getResultsPublicationStatus(), request.resultsPublicationStatus());
        event.setPublicationStatus(request.eventPublicationStatus());
        event.setResultsPublicationStatus(request.resultsPublicationStatus());
        Event saved = eventRepository.save(event);
        changeLogRepository.saveAll(changes);
        return toDto(saved, participantInfoRepository.findByEventId(eventId).orElse(null));
    }

    @Transactional(readOnly = true)
    public Event requireEvent(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found"));
    }

    private Event requirePublishedEvent(Long eventId) {
        return eventRepository.findByIdAndPublicationStatus(eventId, EventPublicationStatus.PUBLISHED)
                .filter(event -> event.getEventSeries().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Published event not found"));
    }

    private EventSeries requireSeries(Long seriesId) {
        return eventSeriesRepository.findById(seriesId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_SERIES_NOT_FOUND", "Event series not found"));
    }

    private static boolean matches(Event event, Integer year, Long seriesId, String city, LocalDate date) {
        if (seriesId != null && !seriesId.equals(event.getEventSeries().getId())) return false;
        if (city != null && (event.getLocation() == null || !event.getLocation().equalsIgnoreCase(city))) return false;
        ZoneId zone = ZoneId.of(event.getTimeZone());
        if (year != null && (event.getStartsAt() == null || event.getStartsAt().atZone(zone).getYear() != year)) return false;
        if (date != null) {
            if (event.getStartsAt() == null) return false;
            LocalDate start = event.getStartsAt().atZone(zone).toLocalDate();
            LocalDate end = (event.getEndsAt() == null ? event.getStartsAt() : event.getEndsAt()).atZone(zone).toLocalDate();
            if (date.isBefore(start) || date.isAfter(end)) return false;
        }
        return true;
    }

    private static void validatePage(int page, int size) {
        if (page < 0) throw new InvalidRequestException("INVALID_PAGE", "page must be zero or greater");
        if (size < 1 || size > MAX_CATALOG_PAGE_SIZE) {
            throw new InvalidRequestException("INVALID_PAGE_SIZE", "size must be between 1 and " + MAX_CATALOG_PAGE_SIZE);
        }
    }

    private static void validateDates(Instant startsAt, Instant endsAt) {
        if (startsAt != null && endsAt != null && endsAt.isBefore(startsAt)) {
            throw new InvalidRequestException("INVALID_EVENT_DATES", "endsAt must not be before startsAt");
        }
    }

    private static void validateResultInquirySettings(boolean enabled, Integer windowDays, String email) {
        if (windowDays != null && windowDays <= 0) {
            throw new InvalidRequestException(
                    "INVALID_RESULT_INQUIRY_WINDOW", "windowDays must be greater than zero"
            );
        }
        if (email != null && (email.length() > 320 || !EMAIL_PATTERN.matcher(email).matches())) {
            throw new InvalidRequestException(
                    "INVALID_RESULT_INQUIRY_EMAIL", "email must be a valid email address"
            );
        }
        if (enabled && windowDays == null) {
            throw new InvalidRequestException(
                    "RESULT_INQUIRY_WINDOW_REQUIRED", "windowDays is required when result inquiry is enabled"
            );
        }
        if (enabled && email == null) {
            throw new InvalidRequestException(
                    "RESULT_INQUIRY_EMAIL_REQUIRED", "email is required when result inquiry is enabled"
            );
        }
    }

    private static void apply(Event event, String name, String slug, Instant startsAt, Instant endsAt, String location,
                              String timeZone, EventPublicationStatus status) {
        event.setName(name.strip());
        event.setSlug(slug.strip());
        event.setStartsAt(startsAt);
        event.setEndsAt(endsAt);
        event.setLocation(normalizeNullable(location));
        event.setTimeZone(normalizedTimeZone(timeZone));
        event.setPublicationStatus(status);
    }

    private static String slugSource(String requestedSlug, String name) {
        return requestedSlug == null || requestedSlug.isBlank() ? name : requestedSlug.strip();
    }

    private static void addChange(List<AdminChangeLog> changes, String actor, Long id, String field, Object oldValue, Object newValue) {
        if (Objects.equals(oldValue, newValue)) return;
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.EVENT);
        log.setEntityId(id);
        log.setFieldName(field);
        log.setOldValue(oldValue == null ? null : oldValue.toString());
        log.setNewValue(newValue == null ? null : newValue.toString());
        changes.add(log);
    }

    public static EventDto toDto(Event event) {
        return toDto(event, null);
    }

    public static EventDto toDto(Event event, EventParticipantInfo info) {
        return new EventDto(
                event.getId(), event.getEventSeries().getId(), event.getEventSeries().getName(), event.getEventSeries().getSlug(),
                event.getName(), event.getSlug(), event.getStartsAt(), event.getEndsAt(), event.getLocation(), event.getTimeZone(),
                event.getPublicationStatus(), event.getResultsPublicationStatus(),
                EventPhaseCalculator.calculate(event, Instant.now()),
                info == null ? null : info.getShortDescription(),
                info == null ? null : info.getVenueName(),
                event.getResultsPublicationStatus() == ResultsPublicationStatus.PUBLISHED
        );
    }

    private EventDto toPublicDto(Event event, EventParticipantInfo info) {
        boolean anyPublishedResults = raceRepository
                .existsByEventIdAndPublicVisibleTrueAndResultsPublicationStatus(
                        event.getId(), ResultsPublicationStatus.PUBLISHED
                );
        ResultsPublicationStatus status = anyPublishedResults
                ? ResultsPublicationStatus.PUBLISHED
                : ResultsPublicationStatus.DRAFT;
        return new EventDto(
                event.getId(), event.getEventSeries().getId(), event.getEventSeries().getName(),
                event.getEventSeries().getSlug(), event.getName(), event.getSlug(), event.getStartsAt(),
                event.getEndsAt(), event.getLocation(), event.getTimeZone(), event.getPublicationStatus(), status,
                EventPhaseCalculator.calculate(event, Instant.now()),
                info == null ? null : info.getShortDescription(),
                info == null ? null : info.getVenueName(),
                anyPublishedResults
        );
    }

    private static ResultInquirySettingsDto inquirySettings(Event event) {
        return new ResultInquirySettingsDto(
                event.isResultInquiryEnabled(),
                event.getResultInquiryWindowDays(),
                event.getResultInquiryEmail()
        );
    }

    private static RaceDto toDto(Race race, boolean categoryStandingEnabled) {
        return new RaceDto(
                race.getId(), race.getEvent().getId(), race.getSourceCode(), race.getName(), race.getSlug(),
                race.getDistanceMeters(), race.getStartsAt(), race.getDisplayOrder(),
                race.getPublicRankingBasis(), categoryStandingEnabled, race.isPublicVisible(),
                race.getResultsPublicationStatus(),
                race.getResultsPublicationStatus() == ResultsPublicationStatus.PUBLISHED,
                race.isResultRecalculationRequired()
        );
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String normalizedTimeZone(String value) {
        String normalized = value == null || value.isBlank() ? "Europe/Moscow" : value.strip();
        try {
            ZoneId zone = ZoneId.of(normalized);
            if (!ZoneId.getAvailableZoneIds().contains(normalized)) {
                throw new java.time.DateTimeException("A named IANA zone is required");
            }
            return zone.getId();
        } catch (java.time.DateTimeException exception) {
            throw new InvalidRequestException("INVALID_TIME_ZONE", "timeZone must be a valid IANA zone ID");
        }
    }
}
