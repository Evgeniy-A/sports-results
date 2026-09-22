package ru.sportsresults.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.AdminCategoryDto;
import ru.sportsresults.api.dto.UpsertCategoryRequest;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.CategoryGender;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.Race;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.CategoryRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RegistrationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class CategoryAdminService {

    private final CategoryRepository categoryRepository;
    private final RaceRepository raceRepository;
    private final RegistrationRepository registrationRepository;
    private final ResultConfigurationChangeGuard configurationGuard;
    private final AdminChangeLogRepository auditRepository;
    private final EventResultDataMutationGuard mutationGuard;

    public CategoryAdminService(
            CategoryRepository categoryRepository,
            RaceRepository raceRepository,
            RegistrationRepository registrationRepository,
            ResultConfigurationChangeGuard configurationGuard,
            AdminChangeLogRepository auditRepository,
            EventResultDataMutationGuard mutationGuard
    ) {
        this.categoryRepository = categoryRepository;
        this.raceRepository = raceRepository;
        this.registrationRepository = registrationRepository;
        this.configurationGuard = configurationGuard;
        this.auditRepository = auditRepository;
        this.mutationGuard = mutationGuard;
    }

    @Transactional(readOnly = true)
    public List<AdminCategoryDto> list(Long raceId) {
        requireRace(raceId);
        return categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(raceId).stream()
                .map(CategoryAdminService::toDto)
                .toList();
    }

    @Transactional
    public AdminCategoryDto create(Long raceId, UpsertCategoryRequest request, String actor) {
        Event event = mutationGuard.lock(eventId(raceId));
        Race race = requireRace(raceId);
        configurationGuard.requireDraft(List.of(race));
        validateSourceName(raceId, request.sourceName().strip(), null);
        Category category = new Category();
        category.setRace(race);
        apply(category, request);
        validate(category, null);
        try {
            Category saved = categoryRepository.saveAndFlush(category);
            auditRepository.save(log(actor, saved.getId(), "created", null, toDto(saved)));
            configurationGuard.markRecalculationRequiredForCurrentData(List.of(race));
            mutationGuard.bump(event);
            return toDto(saved);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateSourceName();
        }
    }

    @Transactional
    public AdminCategoryDto update(Long raceId, Long categoryId, UpsertCategoryRequest request, String actor) {
        Event event = mutationGuard.lock(eventId(raceId));
        Category category = requireCategory(raceId, categoryId);
        Race race = category.getRace();
        validateSourceName(raceId, request.sourceName().strip(), categoryId);
        List<AdminChangeLog> changes = new ArrayList<>();
        addChange(changes, actor, categoryId, "sourceName", category.getSourceName(), request.sourceName().strip());
        addChange(changes, actor, categoryId, "displayName", category.getDisplayName(), request.displayName().strip());
        addChange(changes, actor, categoryId, "minAge", category.getMinAge(), request.minAge());
        addChange(changes, actor, categoryId, "maxAge", category.getMaxAge(), request.maxAge());
        addChange(changes, actor, categoryId, "gender", category.getGender(), request.gender());
        addChange(changes, actor, categoryId, "displayOrder", category.getDisplayOrder(), request.displayOrder());
        addChange(changes, actor, categoryId, "enabled", category.isEnabled(), request.enabled());
        boolean resultAffecting = !Objects.equals(category.getSourceName(), request.sourceName().strip())
                || !Objects.equals(category.getMinAge(), request.minAge())
                || !Objects.equals(category.getMaxAge(), request.maxAge())
                || !Objects.equals(category.getGender(), request.gender())
                || category.isEnabled() != request.enabled();
        if (resultAffecting) {
            configurationGuard.requireDraft(List.of(race));
        }
        apply(category, request);
        validate(category, categoryId);
        try {
            Category saved = categoryRepository.saveAndFlush(category);
            if (!changes.isEmpty()) {
                auditRepository.saveAll(changes);
                if (resultAffecting) {
                    configurationGuard.markRecalculationRequiredForCurrentData(List.of(race));
                    mutationGuard.bump(event);
                }
            }
            return toDto(saved);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateSourceName();
        }
    }

    @Transactional
    public void delete(Long raceId, Long categoryId, String actor) {
        Event event = mutationGuard.lock(eventId(raceId));
        Category category = requireCategory(raceId, categoryId);
        Race race = category.getRace();
        configurationGuard.requireDraft(List.of(race));
        if (registrationRepository.existsByCategoryId(categoryId)) {
            throw new RequestConflictException(
                    "CATEGORY_IN_USE",
                    "A Category referenced by current or historical registrations cannot be deleted"
            );
        }
        AdminCategoryDto oldValue = toDto(category);
        categoryRepository.delete(category);
        categoryRepository.flush();
        auditRepository.save(log(actor, categoryId, "deleted", oldValue, null));
        configurationGuard.markRecalculationRequiredForCurrentData(List.of(race));
        mutationGuard.bump(event);
    }

    private void validate(Category candidate, Long excludedId) {
        CategoryDefinitionValidator.validate(
                new CategoryDefinitionValidator.Rule(
                        excludedId,
                        candidate.getMinAge(),
                        candidate.getMaxAge(),
                        candidate.getGender(),
                        candidate.isEnabled()
                ),
                categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(candidate.getRace().getId()).stream()
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

    private void validateSourceName(Long raceId, String sourceName, Long excludedId) {
        categoryRepository.findByRaceIdAndSourceName(raceId, sourceName)
                .filter(existing -> !existing.getId().equals(excludedId))
                .ifPresent(existing -> {
                    throw duplicateSourceName();
                });
    }

    private Race requireRace(Long raceId) {
        return raceRepository.findById(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found"));
    }

    private Long eventId(Long raceId) {
        return raceRepository.findEventIdByRaceId(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found"));
    }

    private Category requireCategory(Long raceId, Long categoryId) {
        return categoryRepository.findByIdAndRaceId(categoryId, raceId)
                .orElseThrow(() -> new ResourceNotFoundException("CATEGORY_NOT_FOUND", "Category not found"));
    }

    private static void apply(Category category, UpsertCategoryRequest request) {
        category.setSourceName(request.sourceName().strip());
        category.setDisplayName(request.displayName().strip());
        category.setMinAge(request.minAge());
        category.setMaxAge(request.maxAge());
        category.setGender(request.gender());
        category.setDisplayOrder(request.displayOrder());
        category.setEnabled(request.enabled());
    }

    private static void addChange(
            List<AdminChangeLog> logs,
            String actor,
            Long categoryId,
            String field,
            Object oldValue,
            Object newValue
    ) {
        if (!Objects.equals(oldValue, newValue)) {
            logs.add(log(actor, categoryId, field, oldValue, newValue));
        }
    }

    private static AdminChangeLog log(
            String actor,
            Long categoryId,
            String field,
            Object oldValue,
            Object newValue
    ) {
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.CATEGORY);
        log.setEntityId(categoryId);
        log.setFieldName(field);
        log.setOldValue(oldValue == null ? null : oldValue.toString());
        log.setNewValue(newValue == null ? null : newValue.toString());
        return log;
    }

    private static RequestConflictException duplicateSourceName() {
        return new RequestConflictException(
                "CATEGORY_SOURCE_NAME_EXISTS",
                "A category with this sourceName already exists for the Race"
        );
    }

    public static AdminCategoryDto toDto(Category category) {
        return new AdminCategoryDto(
                category.getId(),
                category.getRace().getId(),
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
