package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.ResultRecalculationApplyDto;
import ru.sportsresults.api.dto.ResultRecalculationPreviewDto;
import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.AwardPolicy;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.ResultRecalculationOperation;
import ru.sportsresults.domain.ResultRecalculationOperationStatus;
import ru.sportsresults.repository.AwardPolicyRepository;
import ru.sportsresults.repository.CategoryAssignmentRepository;
import ru.sportsresults.repository.CategoryRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RegistrationRepository;
import ru.sportsresults.repository.ResultRecalculationOperationRepository;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ResultRecalculationService {

    private static final int DEFAULT_ROW_LIMIT = 100;
    private static final int MAX_ROW_LIMIT = 500;

    private final EventResultDataMutationGuard mutationGuard;
    private final ResultConfigurationChangeGuard configurationGuard;
    private final RaceRepository raceRepository;
    private final RegistrationRepository registrationRepository;
    private final CategoryRepository categoryRepository;
    private final AwardPolicyRepository awardPolicyRepository;
    private final CategoryAssignmentRepository assignmentRepository;
    private final ResultRecalculationOperationRepository operationRepository;
    private final AgeCategoryResolver categoryResolver;
    private final ObjectMapper objectMapper;

    public ResultRecalculationService(
            EventResultDataMutationGuard mutationGuard,
            ResultConfigurationChangeGuard configurationGuard,
            RaceRepository raceRepository,
            RegistrationRepository registrationRepository,
            CategoryRepository categoryRepository,
            AwardPolicyRepository awardPolicyRepository,
            CategoryAssignmentRepository assignmentRepository,
            ResultRecalculationOperationRepository operationRepository,
            AgeCategoryResolver categoryResolver,
            ObjectMapper objectMapper
    ) {
        this.mutationGuard = mutationGuard;
        this.configurationGuard = configurationGuard;
        this.raceRepository = raceRepository;
        this.registrationRepository = registrationRepository;
        this.categoryRepository = categoryRepository;
        this.awardPolicyRepository = awardPolicyRepository;
        this.assignmentRepository = assignmentRepository;
        this.operationRepository = operationRepository;
        this.categoryResolver = categoryResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ResultRecalculationPreviewDto preview(
            Long eventId,
            List<Long> requestedRaceIds,
            Integer requestedRowLimit,
            String actor
    ) {
        validateActor(actor);
        int rowLimit = rowLimit(requestedRowLimit);
        Event event = mutationGuard.lock(eventId);
        List<Race> races = scope(eventId, requestedRaceIds);
        configurationGuard.requireDraft(races);
        if (races.stream().anyMatch(race -> !race.isResultRecalculationRequired())) {
            throw new RequestConflictException(
                    "RECALCULATION_NOT_REQUIRED",
                    "Every selected Race must have a pending result recalculation"
            );
        }

        Plan plan = plan(event, races);
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        UUID operationId = UUID.randomUUID();
        ResultRecalculationPreviewDto response = response(
                operationId,
                ResultRecalculationOperationStatus.PREVIEWED,
                event,
                races,
                plan,
                createdAt,
                createdAt.plus(24, ChronoUnit.HOURS),
                rowLimit
        );

        ResultRecalculationOperation operation = new ResultRecalculationOperation();
        operation.setId(operationId);
        operation.setEvent(event);
        operation.setRaces(new LinkedHashSet<>(races));
        operation.setBaseRevision(event.getResultDataRevision());
        operation.setConfigurationDigest(plan.configurationDigest());
        operation.setPlanDigest(plan.planDigest());
        operation.setPreviewSummary(json(response));
        operation.setCurrentCount(plan.currentCount());
        operation.setChangedCount(plan.changedCount());
        operation.setBlockingCount(plan.blockingCount());
        operation.setCreatedBy(actor.strip());
        operation.setCreatedAt(createdAt);
        operation.setExpiresAt(createdAt.plus(24, ChronoUnit.HOURS));
        operationRepository.saveAndFlush(operation);
        return response;
    }

    @Transactional
    public ResultRecalculationApplyDto apply(Long eventId, UUID operationId, String actor) {
        validateActor(actor);
        Event event = mutationGuard.lock(eventId);
        ResultRecalculationOperation operation = operationRepository.findByIdAndEventIdForUpdate(
                        operationId, eventId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RECALCULATION_OPERATION_NOT_FOUND", "Result recalculation preview not found"
                ));
        if (operation.getStatus() == ResultRecalculationOperationStatus.APPLIED) {
            return appliedResponse(operation);
        }
        if (operation.getStatus() != ResultRecalculationOperationStatus.PREVIEWED
                || operation.getExpiresAt() != null && !Instant.now().isBefore(operation.getExpiresAt())) {
            throw stale();
        }
        if (event.getResultDataRevision() != operation.getBaseRevision()) {
            throw stale();
        }

        List<Long> raceIds = operation.getRaces().stream().map(Race::getId).sorted().toList();
        List<Race> races = scope(eventId, raceIds);
        configurationGuard.requireDraft(races);
        if (races.stream().anyMatch(race -> !race.isResultRecalculationRequired())) {
            throw stale();
        }
        Plan current = plan(event, races);
        if (!operation.getConfigurationDigest().equals(current.configurationDigest())
                || !operation.getPlanDigest().equals(current.planDigest())
                || operation.getCurrentCount() != current.currentCount()
                || operation.getChangedCount() != current.changedCount()
                || operation.getBlockingCount() != current.blockingCount()) {
            throw stale();
        }
        if (current.blockingCount() > 0) {
            throw new RequestConflictException(
                    "RECALCULATION_BLOCKED",
                    "Result recalculation contains blocking category rows"
            );
        }

        assignmentRepository.updateAll(current.assignments());
        races.forEach(race -> race.setResultRecalculationRequired(false));
        raceRepository.saveAllAndFlush(races);
        long newRevision = mutationGuard.bump(event);

        Instant appliedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        operation.setStatus(ResultRecalculationOperationStatus.APPLIED);
        operation.setAppliedBy(actor.strip());
        operation.setAppliedAt(appliedAt);
        operation.setNewRevision(newRevision);
        operationRepository.saveAndFlush(operation);
        return appliedResponse(operation);
    }

    private Plan plan(Event event, List<Race> races) {
        List<Long> raceIds = races.stream().map(Race::getId).sorted().toList();
        List<Registration> registrations = registrationRepository.findAllCurrentByRaceIds(raceIds).stream()
                .sorted(Comparator.comparing(Registration::getId))
                .toList();
        Map<Long, List<Category>> categoriesByRace = categoryRepository.findAllByRaceEventId(event.getId()).stream()
                .filter(category -> raceIds.contains(category.getRace().getId()))
                .sorted(Comparator.comparing((Category category) -> category.getRace().getId())
                        .thenComparing(Category::getDisplayOrder)
                        .thenComparing(Category::getId))
                .collect(Collectors.groupingBy(
                        category -> category.getRace().getId(), LinkedHashMap::new, Collectors.toList()
                ));
        Map<Long, AwardPolicy> policies = awardPolicyRepository.findAllByRaceEventId(event.getId()).stream()
                .filter(policy -> raceIds.contains(policy.getRace().getId()))
                .collect(Collectors.toMap(
                        policy -> policy.getRace().getId(), Function.identity(), (left, right) -> left
                ));
        String configurationDigest = configurationDigest(event, races, categoriesByRace, policies);

        int minorCount = 0;
        int adultCount = 0;
        int noBirthDateCount = 0;
        int minorSourceCategoryCount = 0;
        int adultCalculatedCategoryCount = 0;
        int adultWithoutCategoryCount = 0;
        int unchangedCount = 0;
        int changedCount = 0;
        int blockingCount = 0;
        List<PlannedRow> rows = new ArrayList<>(registrations.size());
        List<CategoryAssignmentRepository.CategoryAssignment> assignments = new ArrayList<>();

        for (Registration registration : registrations) {
            Long raceId = registration.getRace().getId();
            AwardPolicy policy = policies.get(raceId);
            AgeCalculationMode mode = policy == null
                    ? AgeCalculationMode.EVENT_DATE
                    : policy.getAgeCalculationMode();
            AgeCategoryResolution<Category> resolution = categoryResolver.resolveDetailed(
                    registration.getBirthDate(),
                    registration.getGender(),
                    registration.getSourceCategory(),
                    event.getStartsAt(),
                    event.getTimeZone(),
                    mode,
                    categoriesByRace.getOrDefault(raceId, List.of())
            );
            if (resolution.branch() == AgeCategoryBranch.MINOR) {
                minorCount++;
                if (registration.getSourceCategory() != null && !registration.getSourceCategory().isBlank()) {
                    minorSourceCategoryCount++;
                }
            } else if (resolution.branch() == AgeCategoryBranch.ADULT) {
                adultCount++;
                if (!resolution.blocked() && resolution.category() != null) {
                    adultCalculatedCategoryCount++;
                } else if (!resolution.blocked()) {
                    adultWithoutCategoryCount++;
                }
            } else {
                noBirthDateCount++;
            }

            Long oldCategoryId = registration.getCategory() == null ? null : registration.getCategory().getId();
            Long newCategoryId = resolution.category() == null ? null : resolution.category().getId();
            boolean changed = !resolution.blocked() && !Objects.equals(oldCategoryId, newCategoryId);
            if (resolution.blocked()) {
                blockingCount++;
            } else if (changed) {
                changedCount++;
                assignments.add(new CategoryAssignmentRepository.CategoryAssignment(
                        registration.getId(), newCategoryId
                ));
            } else {
                unchangedCount++;
            }
            rows.add(new PlannedRow(
                    registration.getId(),
                    registration.getBib(),
                    oldCategoryId,
                    categoryName(registration.getCategory()),
                    newCategoryId,
                    categoryName(resolution.category()),
                    changed ? resolution.reason().name() : resolution.blocked()
                            ? resolution.reason().name() : "UNCHANGED",
                    resolution.blockingCode(),
                    registration.getBirthDate() == null ? null : registration.getBirthDate().toString(),
                    registration.getGender(),
                    registration.getSourceCategory(),
                    raceId
            ));
        }
        String planDigest = planDigest(
                event.getResultDataRevision(), configurationDigest, raceIds, rows
        );
        return new Plan(
                configurationDigest,
                planDigest,
                List.copyOf(rows),
                List.copyOf(assignments),
                registrations.size(),
                minorCount,
                adultCount,
                noBirthDateCount,
                minorSourceCategoryCount,
                adultCalculatedCategoryCount,
                adultWithoutCategoryCount,
                unchangedCount,
                changedCount,
                blockingCount,
                races.stream().anyMatch(Race::isResultRecalculationRequired)
        );
    }

    private ResultRecalculationPreviewDto response(
            UUID operationId,
            ResultRecalculationOperationStatus status,
            Event event,
            List<Race> races,
            Plan plan,
            Instant createdAt,
            Instant expiresAt,
            int rowLimit
    ) {
        List<ResultRecalculationPreviewDto.Row> rows = plan.rows().stream()
                .limit(rowLimit)
                .map(row -> new ResultRecalculationPreviewDto.Row(
                        row.registrationId(),
                        row.bib(),
                        row.oldCategoryName(),
                        row.newCategoryName(),
                        row.reason(),
                        row.blockingCode()
                ))
                .toList();
        return new ResultRecalculationPreviewDto(
                operationId,
                status,
                new ResultRecalculationPreviewDto.EventRef(event.getId(), event.getName()),
                races.stream().map(race -> new ResultRecalculationPreviewDto.RaceRef(
                        race.getId(), race.getName()
                )).toList(),
                event.getResultDataRevision(),
                plan.configurationDigest(),
                plan.planDigest(),
                createdAt,
                expiresAt,
                plan.currentCount(),
                plan.minorCount(),
                plan.adultCount(),
                plan.noBirthDateCount(),
                plan.minorSourceCategoryCount(),
                plan.adultCalculatedCategoryCount(),
                plan.adultWithoutCategoryCount(),
                plan.unchangedCount(),
                plan.changedCount(),
                plan.blockingCount(),
                plan.rankingAffected(),
                rowLimit,
                plan.rows().size() > rowLimit,
                rows
        );
    }

    private List<Race> scope(Long eventId, List<Long> requestedRaceIds) {
        if (requestedRaceIds == null || requestedRaceIds.isEmpty()
                || requestedRaceIds.stream().anyMatch(Objects::isNull)) {
            throw new InvalidRequestException(
                    "RECALCULATION_SCOPE_REQUIRED", "At least one Race must be selected"
            );
        }
        List<Long> canonical = requestedRaceIds.stream().distinct().sorted().toList();
        Map<Long, Race> eventRaces = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(eventId).stream()
                .collect(Collectors.toMap(Race::getId, Function.identity()));
        if (!eventRaces.keySet().containsAll(canonical)) {
            throw new InvalidRequestException(
                    "INVALID_RECALCULATION_SCOPE", "Every selected Race must belong to the Event"
            );
        }
        return canonical.stream().map(eventRaces::get).toList();
    }

    private static String configurationDigest(
            Event event,
            List<Race> races,
            Map<Long, List<Category>> categoriesByRace,
            Map<Long, AwardPolicy> policies
    ) {
        DigestWriter digest = new DigestWriter()
                .add(event.getId())
                .add(event.getStartsAt())
                .add(event.getTimeZone());
        for (Race race : races.stream().sorted(Comparator.comparing(Race::getId)).toList()) {
            digest.add(race.getId())
                    .add(race.getResultsPublicationStatus())
                    .add(race.isResultRecalculationRequired());
            AwardPolicy policy = policies.get(race.getId());
            if (policy == null) {
                digest.add("NO_POLICY").add(AgeCalculationMode.EVENT_DATE);
            } else {
                digest.add(policy.getId())
                        .add(policy.getRankingBasis())
                        .add(policy.getPrimaryStandingMode())
                        .add(policy.getAbsolutePrizePlaces())
                        .add(policy.isCategoryEnabled())
                        .add(policy.getAgeCalculationMode())
                        .add(policy.getCategoryPrizePlaces())
                        .add(policy.isExcludeAbsoluteWinnersFromCategory());
            }
            for (Category category : categoriesByRace.getOrDefault(race.getId(), List.of())) {
                digest.add(category.getId())
                        .add(category.getSourceName())
                        .add(category.getDisplayName())
                        .add(category.getDisplayOrder())
                        .add(category.getMinAge())
                        .add(category.getMaxAge())
                        .add(category.getGender())
                        .add(category.isEnabled());
            }
        }
        return digest.finish();
    }

    private static String planDigest(
            long resultDataRevision,
            String configurationDigest,
            List<Long> raceIds,
            List<PlannedRow> rows
    ) {
        DigestWriter digest = new DigestWriter()
                .add(resultDataRevision)
                .add(configurationDigest);
        raceIds.forEach(digest::add);
        for (PlannedRow row : rows) {
            digest.add(row.registrationId())
                    .add(row.raceId())
                    .add(row.bib())
                    .add(row.birthDate())
                    .add(row.gender())
                    .add(row.sourceCategory())
                    .add(row.oldCategoryId())
                    .add(row.newCategoryId())
                    .add(row.reason())
                    .add(row.blockingCode());
        }
        return digest.finish();
    }

    private static String categoryName(Category category) {
        return category == null ? null : category.getDisplayName();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Recalculation preview could not be serialized", exception);
        }
    }

    private static ResultRecalculationApplyDto appliedResponse(ResultRecalculationOperation operation) {
        return new ResultRecalculationApplyDto(
                operation.getId(),
                operation.getStatus(),
                operation.getEvent().getId(),
                operation.getRaces().stream().map(Race::getId).sorted().toList(),
                operation.getChangedCount(),
                operation.getAppliedAt(),
                operation.getNewRevision()
        );
    }

    private static void validateActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRequestException(
                    "RECALCULATION_ACTOR_REQUIRED", "Authenticated recalculation actor is required"
            );
        }
    }

    private static int rowLimit(Integer requested) {
        int value = requested == null ? DEFAULT_ROW_LIMIT : requested;
        if (value < 0 || value > MAX_ROW_LIMIT) {
            throw new InvalidRequestException(
                    "INVALID_RECALCULATION_ROW_LIMIT",
                    "rowLimit must be between 0 and " + MAX_ROW_LIMIT
            );
        }
        return value;
    }

    private static RequestConflictException stale() {
        return new RequestConflictException(
                "RECALC_PREVIEW_STALE",
                "Result recalculation preview no longer matches current configuration or registrations"
        );
    }

    private record Plan(
            String configurationDigest,
            String planDigest,
            List<PlannedRow> rows,
            List<CategoryAssignmentRepository.CategoryAssignment> assignments,
            int currentCount,
            int minorCount,
            int adultCount,
            int noBirthDateCount,
            int minorSourceCategoryCount,
            int adultCalculatedCategoryCount,
            int adultWithoutCategoryCount,
            int unchangedCount,
            int changedCount,
            int blockingCount,
            boolean rankingAffected
    ) {
    }

    private record PlannedRow(
            Long registrationId,
            String bib,
            Long oldCategoryId,
            String oldCategoryName,
            Long newCategoryId,
            String newCategoryName,
            String reason,
            String blockingCode,
            String birthDate,
            String gender,
            String sourceCategory,
            Long raceId
    ) {
    }

    private static final class DigestWriter {
        private final MessageDigest digest;

        private DigestWriter() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is not available", exception);
            }
        }

        private DigestWriter add(Object value) {
            byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
            digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
            digest.update((byte) ':');
            digest.update(bytes);
            digest.update((byte) '\n');
            return this;
        }

        private String finish() {
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
