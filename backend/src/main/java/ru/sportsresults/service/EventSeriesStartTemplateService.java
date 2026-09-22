package ru.sportsresults.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.EventSeriesStartAwardPolicyTemplateDto;
import ru.sportsresults.api.dto.EventSeriesStartCategoryTemplateDto;
import ru.sportsresults.api.dto.EventSeriesStartTemplateDto;
import ru.sportsresults.api.dto.ReorderEventSeriesStartTemplatesRequest;
import ru.sportsresults.api.dto.UpdateAwardPolicyRequest;
import ru.sportsresults.api.dto.UpsertCategoryRequest;
import ru.sportsresults.api.dto.UpsertEventSeriesStartTemplateRequest;
import ru.sportsresults.domain.EventSeries;
import ru.sportsresults.domain.EventSeriesStartAwardPolicyTemplate;
import ru.sportsresults.domain.EventSeriesStartCategoryTemplate;
import ru.sportsresults.domain.EventSeriesStartTemplate;
import ru.sportsresults.repository.EventSeriesRepository;
import ru.sportsresults.repository.EventSeriesStartAwardPolicyTemplateRepository;
import ru.sportsresults.repository.EventSeriesStartCategoryTemplateRepository;
import ru.sportsresults.repository.EventSeriesStartTemplateRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EventSeriesStartTemplateService {
    private final EventSeriesRepository eventSeriesRepository;
    private final EventSeriesStartTemplateRepository templateRepository;
    private final EventSeriesStartAwardPolicyTemplateRepository policyRepository;
    private final EventSeriesStartCategoryTemplateRepository categoryRepository;

    public EventSeriesStartTemplateService(
            EventSeriesRepository eventSeriesRepository,
            EventSeriesStartTemplateRepository templateRepository,
            EventSeriesStartAwardPolicyTemplateRepository policyRepository,
            EventSeriesStartCategoryTemplateRepository categoryRepository
    ) {
        this.eventSeriesRepository = eventSeriesRepository;
        this.templateRepository = templateRepository;
        this.policyRepository = policyRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<EventSeriesStartTemplateDto> list(Long eventSeriesId) {
        requireSeries(eventSeriesId);
        List<EventSeriesStartAwardPolicyTemplate> policies =
                policyRepository.findAllByTemplateStartEventSeriesId(eventSeriesId);
        Map<Long, EventSeriesStartAwardPolicyTemplate> policiesByStart = policies.stream()
                .collect(Collectors.toMap(policy -> policy.getTemplateStart().getId(), policy -> policy));
        Map<Long, List<EventSeriesStartCategoryTemplate>> categoriesByStart = categoryRepository
                .findAllByEventSeriesId(eventSeriesId).stream()
                .collect(Collectors.groupingBy(
                        category -> category.getTemplateStart().getId(),
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));
        return templateRepository.findAllByEventSeriesIdOrderByDisplayOrderAscIdAsc(eventSeriesId).stream()
                .map(template -> toDto(
                        template,
                        policiesByStart.get(template.getId()),
                        categoriesByStart.getOrDefault(template.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public EventSeriesStartTemplateDto create(Long eventSeriesId, UpsertEventSeriesStartTemplateRequest request) {
        EventSeries series = lockSeries(eventSeriesId);
        List<EventSeriesStartTemplate> current = templateRepository.findAllByEventSeriesIdForUpdate(eventSeriesId);
        EventSeriesStartTemplate template = new EventSeriesStartTemplate();
        template.setEventSeries(series);
        template.setDisplayOrder(current.size());
        apply(template, request);
        return toDto(templateRepository.saveAndFlush(template), null, List.of());
    }

    @Transactional
    public EventSeriesStartTemplateDto update(
            Long eventSeriesId,
            Long templateStartId,
            UpsertEventSeriesStartTemplateRequest request
    ) {
        lockSeries(eventSeriesId);
        EventSeriesStartTemplate template = requireTemplate(eventSeriesId, templateStartId);
        apply(template, request);
        EventSeriesStartTemplate saved = templateRepository.saveAndFlush(template);
        return toDto(
                saved,
                policyRepository.findByTemplateStartId(templateStartId).orElse(null),
                categoryRepository.findAllByTemplateStartIdOrderByDisplayOrderAscIdAsc(templateStartId)
        );
    }

    @Transactional
    public List<EventSeriesStartTemplateDto> reorder(
            Long eventSeriesId,
            ReorderEventSeriesStartTemplatesRequest request
    ) {
        lockSeries(eventSeriesId);
        List<EventSeriesStartTemplate> templates = templateRepository.findAllByEventSeriesIdForUpdate(eventSeriesId);
        List<Long> requestedIds = request.templateStartIds();
        Set<Long> uniqueIds = new HashSet<>(requestedIds);
        Set<Long> existingIds = templates.stream().map(EventSeriesStartTemplate::getId).collect(Collectors.toSet());
        if (uniqueIds.size() != requestedIds.size() || !uniqueIds.equals(existingIds)) {
            throw new InvalidRequestException(
                    "INVALID_TEMPLATE_START_ORDER",
                    "Reorder must contain every start from this template exactly once"
            );
        }
        Map<Long, EventSeriesStartTemplate> byId = templates.stream()
                .collect(Collectors.toMap(EventSeriesStartTemplate::getId, value -> value));
        for (int index = 0; index < requestedIds.size(); index++) {
            byId.get(requestedIds.get(index)).setDisplayOrder(index);
        }
        templateRepository.saveAllAndFlush(templates);
        return list(eventSeriesId);
    }

    @Transactional
    public void delete(Long eventSeriesId, Long templateStartId) {
        lockSeries(eventSeriesId);
        EventSeriesStartTemplate template = requireTemplate(eventSeriesId, templateStartId);
        templateRepository.delete(template);
        templateRepository.flush();
        normalizeOrders(eventSeriesId);
    }

    @Transactional(readOnly = true)
    public EventSeriesStartAwardPolicyTemplateDto getAwardPolicy(Long eventSeriesId, Long templateStartId) {
        requireTemplate(eventSeriesId, templateStartId);
        return policyRepository.findByTemplateStartId(templateStartId)
                .map(EventSeriesStartTemplateService::toPolicyDto)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEMPLATE_AWARD_POLICY_NOT_FOUND", "Template award policy not configured"
                ));
    }

    @Transactional
    public EventSeriesStartAwardPolicyTemplateDto upsertAwardPolicy(
            Long eventSeriesId,
            Long templateStartId,
            UpdateAwardPolicyRequest request
    ) {
        lockSeries(eventSeriesId);
        EventSeriesStartTemplate template = requireTemplate(eventSeriesId, templateStartId);
        AwardPolicyService.validate(request);
        EventSeriesStartAwardPolicyTemplate policy = policyRepository.findByTemplateStartId(templateStartId)
                .orElseGet(() -> {
                    EventSeriesStartAwardPolicyTemplate created = new EventSeriesStartAwardPolicyTemplate();
                    created.setTemplateStart(template);
                    return created;
                });
        apply(policy, request);
        return toPolicyDto(policyRepository.saveAndFlush(policy));
    }

    @Transactional
    public void deleteAwardPolicy(Long eventSeriesId, Long templateStartId) {
        lockSeries(eventSeriesId);
        requireTemplate(eventSeriesId, templateStartId);
        policyRepository.deleteByTemplateStartId(templateStartId);
    }

    @Transactional(readOnly = true)
    public List<EventSeriesStartCategoryTemplateDto> listCategories(Long eventSeriesId, Long templateStartId) {
        requireTemplate(eventSeriesId, templateStartId);
        return categoryRepository.findAllByTemplateStartIdOrderByDisplayOrderAscIdAsc(templateStartId).stream()
                .map(EventSeriesStartTemplateService::toCategoryDto)
                .toList();
    }

    @Transactional
    public EventSeriesStartCategoryTemplateDto createCategory(
            Long eventSeriesId,
            Long templateStartId,
            UpsertCategoryRequest request
    ) {
        lockSeries(eventSeriesId);
        EventSeriesStartTemplate template = requireTemplate(eventSeriesId, templateStartId);
        validateSourceName(templateStartId, request.sourceName().strip(), null);
        EventSeriesStartCategoryTemplate category = new EventSeriesStartCategoryTemplate();
        category.setTemplateStart(template);
        apply(category, request);
        validateCategory(category, null);
        try {
            return toCategoryDto(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateCategorySourceName();
        }
    }

    @Transactional
    public EventSeriesStartCategoryTemplateDto updateCategory(
            Long eventSeriesId,
            Long templateStartId,
            Long categoryId,
            UpsertCategoryRequest request
    ) {
        lockSeries(eventSeriesId);
        requireTemplate(eventSeriesId, templateStartId);
        EventSeriesStartCategoryTemplate category = requireCategory(templateStartId, categoryId);
        validateSourceName(templateStartId, request.sourceName().strip(), categoryId);
        apply(category, request);
        validateCategory(category, categoryId);
        try {
            return toCategoryDto(categoryRepository.saveAndFlush(category));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateCategorySourceName();
        }
    }

    @Transactional
    public void deleteCategory(Long eventSeriesId, Long templateStartId, Long categoryId) {
        lockSeries(eventSeriesId);
        requireTemplate(eventSeriesId, templateStartId);
        EventSeriesStartCategoryTemplate category = requireCategory(templateStartId, categoryId);
        categoryRepository.delete(category);
    }

    private void normalizeOrders(Long eventSeriesId) {
        List<EventSeriesStartTemplate> templates = templateRepository.findAllByEventSeriesIdForUpdate(eventSeriesId);
        for (int index = 0; index < templates.size(); index++) {
            templates.get(index).setDisplayOrder(index);
        }
        templateRepository.saveAll(templates);
    }

    private EventSeries requireSeries(Long eventSeriesId) {
        return eventSeriesRepository.findById(eventSeriesId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EVENT_SERIES_NOT_FOUND", "Event series not found"
                ));
    }

    private EventSeries lockSeries(Long eventSeriesId) {
        return eventSeriesRepository.findByIdForUpdate(eventSeriesId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EVENT_SERIES_NOT_FOUND", "Event series not found"
                ));
    }

    private EventSeriesStartTemplate requireTemplate(Long eventSeriesId, Long templateStartId) {
        return templateRepository.findByIdAndEventSeriesId(templateStartId, eventSeriesId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEMPLATE_START_NOT_FOUND", "Template start not found"
                ));
    }

    private EventSeriesStartCategoryTemplate requireCategory(Long templateStartId, Long categoryId) {
        return categoryRepository.findByIdAndTemplateStartId(categoryId, templateStartId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TEMPLATE_CATEGORY_NOT_FOUND", "Template category not found"
                ));
    }

    private void validateSourceName(Long templateStartId, String sourceName, Long excludedId) {
        categoryRepository.findByTemplateStartIdAndSourceName(templateStartId, sourceName)
                .filter(existing -> !existing.getId().equals(excludedId))
                .ifPresent(existing -> {
                    throw duplicateCategorySourceName();
                });
    }

    private void validateCategory(EventSeriesStartCategoryTemplate candidate, Long excludedId) {
        CategoryDefinitionValidator.validate(
                new CategoryDefinitionValidator.Rule(
                        excludedId,
                        candidate.getMinAge(),
                        candidate.getMaxAge(),
                        candidate.getGender(),
                        candidate.isEnabled()
                ),
                categoryRepository.findAllByTemplateStartIdOrderByDisplayOrderAscIdAsc(
                                candidate.getTemplateStart().getId()).stream()
                        .map(category -> new CategoryDefinitionValidator.Rule(
                                category.getId(),
                                category.getMinAge(),
                                category.getMaxAge(),
                                category.getGender(),
                                category.isEnabled()
                        ))
                        .toList()
        );
    }

    private static void apply(EventSeriesStartCategoryTemplate category, UpsertCategoryRequest request) {
        category.setSourceName(request.sourceName().strip());
        category.setDisplayName(request.displayName().strip());
        category.setMinAge(request.minAge());
        category.setMaxAge(request.maxAge());
        category.setGender(request.gender());
        category.setDisplayOrder(request.displayOrder());
        category.setEnabled(request.enabled());
    }

    private static RequestConflictException duplicateCategorySourceName() {
        return new RequestConflictException(
                "TEMPLATE_CATEGORY_SOURCE_NAME_EXISTS",
                "A category with this sourceName already exists for the TemplateStart"
        );
    }

    private static void apply(
            EventSeriesStartTemplate template,
            UpsertEventSeriesStartTemplateRequest request
    ) {
        template.setName(request.name().strip());
        template.setDistanceMeters(request.distanceMeters());
        if (request.publicVisible() != null) {
            template.setPublicVisible(request.publicVisible());
        }
    }

    static void apply(EventSeriesStartAwardPolicyTemplate policy, UpdateAwardPolicyRequest request) {
        policy.setRankingBasis(request.rankingBasis());
        policy.setPrimaryStandingMode(request.primaryStandingMode());
        policy.setAbsolutePrizePlaces(request.absolutePrizePlaces());
        policy.setCategoryEnabled(request.categoryEnabled());
        policy.setAgeCalculationMode(request.ageCalculationMode());
        policy.setCategoryPrizePlaces(request.categoryPrizePlaces());
        policy.setExcludeAbsoluteWinnersFromCategory(request.excludeAbsoluteWinnersFromCategory());
    }

    public static EventSeriesStartTemplateDto toDto(
            EventSeriesStartTemplate template,
            EventSeriesStartAwardPolicyTemplate policy,
            List<EventSeriesStartCategoryTemplate> categories
    ) {
        return new EventSeriesStartTemplateDto(
                template.getId(), template.getEventSeries().getId(), template.getName(),
                template.getDistanceMeters(), template.getDisplayOrder(),
                template.isPublicVisible(), policy == null ? null : toPolicyDto(policy),
                categories.stream().map(EventSeriesStartTemplateService::toCategoryDto).toList(),
                template.getCreatedAt(), template.getUpdatedAt()
        );
    }

    public static EventSeriesStartAwardPolicyTemplateDto toPolicyDto(
            EventSeriesStartAwardPolicyTemplate policy
    ) {
        return new EventSeriesStartAwardPolicyTemplateDto(
                policy.getId(), policy.getRankingBasis(), policy.getPrimaryStandingMode(),
                policy.getAbsolutePrizePlaces(), policy.isCategoryEnabled(), policy.getAgeCalculationMode(),
                policy.getCategoryPrizePlaces(), policy.isExcludeAbsoluteWinnersFromCategory()
        );
    }

    public static EventSeriesStartCategoryTemplateDto toCategoryDto(
            EventSeriesStartCategoryTemplate category
    ) {
        return new EventSeriesStartCategoryTemplateDto(
                category.getId(),
                category.getTemplateStart().getId(),
                category.getSourceName(),
                category.getDisplayName(),
                category.getMinAge(),
                category.getMaxAge(),
                category.getGender(),
                category.getDisplayOrder(),
                category.isEnabled()
        );
    }
}
