package ru.sportsresults.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.ImportApplyResponseDto;
import ru.sportsresults.api.dto.ImportPreviewResponseDto;
import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.AwardPolicy;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.ImportBatch;
import ru.sportsresults.domain.ImportBatchStatus;
import ru.sportsresults.domain.ImportOperation;
import ru.sportsresults.domain.ImportOperationItem;
import ru.sportsresults.domain.ImportOperationMode;
import ru.sportsresults.domain.ImportOperationStatus;
import ru.sportsresults.domain.ImportScopeType;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.StartCluster;
import ru.sportsresults.domain.ResultsPublicationStatus;
import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.importing.ImportPreviewAction;
import ru.sportsresults.importing.ImportPreviewDecision;
import ru.sportsresults.importing.SourceField;
import ru.sportsresults.importing.TimingResultImportRow;
import ru.sportsresults.repository.AwardPolicyRepository;
import ru.sportsresults.repository.CategoryRepository;
import ru.sportsresults.repository.ImportBatchRepository;
import ru.sportsresults.repository.ImportOperationItemRepository;
import ru.sportsresults.repository.ImportOperationRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RegistrationRepository;
import ru.sportsresults.repository.ResultRepository;
import ru.sportsresults.repository.StartClusterRepository;
import ru.sportsresults.repository.ResultIssueRequestRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
class ImportApplyTransactionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportApplyTransactionService.class);

    private final EventResultDataMutationGuard mutationGuard;
    private final ImportOperationRepository operationRepository;
    private final ImportOperationItemRepository operationItemRepository;
    private final ImportBatchRepository importBatchRepository;
    private final ImportPreviewSnapshotLoader snapshotLoader;
    private final ImportPreviewPlanner planner;
    private final RaceRepository raceRepository;
    private final StartClusterRepository clusterRepository;
    private final CategoryRepository categoryRepository;
    private final AwardPolicyRepository awardPolicyRepository;
    private final RegistrationRepository registrationRepository;
    private final ResultRepository resultRepository;
    private final AgeCategoryResolver ageCategoryResolver;
    private final ResultIssueRequestRepository issueRepository;
    private final ResultIssueLifecycleService issueLifecycleService;

    ImportApplyTransactionService(
            EventResultDataMutationGuard mutationGuard,
            ImportOperationRepository operationRepository,
            ImportOperationItemRepository operationItemRepository,
            ImportBatchRepository importBatchRepository,
            ImportPreviewSnapshotLoader snapshotLoader,
            ImportPreviewPlanner planner,
            RaceRepository raceRepository,
            StartClusterRepository clusterRepository,
            CategoryRepository categoryRepository,
            AwardPolicyRepository awardPolicyRepository,
            RegistrationRepository registrationRepository,
            ResultRepository resultRepository,
            AgeCategoryResolver ageCategoryResolver,
            ResultIssueRequestRepository issueRepository,
            ResultIssueLifecycleService issueLifecycleService
    ) {
        this.mutationGuard = mutationGuard;
        this.operationRepository = operationRepository;
        this.operationItemRepository = operationItemRepository;
        this.importBatchRepository = importBatchRepository;
        this.snapshotLoader = snapshotLoader;
        this.planner = planner;
        this.raceRepository = raceRepository;
        this.clusterRepository = clusterRepository;
        this.categoryRepository = categoryRepository;
        this.awardPolicyRepository = awardPolicyRepository;
        this.registrationRepository = registrationRepository;
        this.resultRepository = resultRepository;
        this.ageCategoryResolver = ageCategoryResolver;
        this.issueRepository = issueRepository;
        this.issueLifecycleService = issueLifecycleService;
    }

    @Transactional
    public ImportApplyResponseDto apply(
            Long eventId,
            UUID operationId,
            PreparedImportFile prepared,
            String actor
    ) {
        // Global order for result-data writes: Event, sorted selected Races, operation.
        Event event = mutationGuard.lock(eventId);
        ImportOperation previewMetadata = operationRepository.findWithEventById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "IMPORT_OPERATION_NOT_FOUND", "Import operation not found"));
        if (!previewMetadata.getEvent().getId().equals(eventId)) {
            throw new InvalidRequestException(
                    "IMPORT_OPERATION_EVENT_MISMATCH", "Import operation belongs to another event");
        }
        List<Long> previewScopeRaceIds = previewMetadata.getRaces().stream()
                .map(Race::getId).sorted().toList();
        if (previewMetadata.getMode() == ImportOperationMode.EMERGENCY_REPLACE) {
            for (Long raceId : previewScopeRaceIds) {
                Race lockedRace = raceRepository.findByIdAndEventIdForUpdate(raceId, eventId)
                        .orElseThrow(() -> new RequestConflictException(
                                "PREVIEW_STALE", "Import Race scope changed after preview"));
                if (lockedRace.getResultsPublicationStatus() != ResultsPublicationStatus.DRAFT) {
                    throw new RequestConflictException(
                            "RACE_RESULTS_MUST_BE_DRAFT",
                            "Every Race in an emergency replacement scope must be DRAFT"
                    );
                }
            }
        }
        ImportOperation operation = operationRepository.findByIdForUpdate(operationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "IMPORT_OPERATION_NOT_FOUND", "Import operation not found"));
        if (!operation.getEvent().getId().equals(eventId)) {
            throw new InvalidRequestException(
                    "IMPORT_OPERATION_EVENT_MISMATCH", "Import operation belongs to another event");
        }
        if (!operation.getFileSha256().equals(prepared.fileSha256())) {
            throw new RequestConflictException("FILE_MISMATCH", "Uploaded file differs from the previewed file");
        }
        if (operation.getStatus() == ImportOperationStatus.APPLIED) {
            return appliedResponse(operation);
        }
        if (operation.getStatus() != ImportOperationStatus.PREVIEWED
                || operation.getExpiresAt() != null && operation.getExpiresAt().isBefore(Instant.now())) {
            throw new RequestConflictException("PREVIEW_STALE", "Import preview is no longer applicable");
        }
        if (event.getResultDataRevision() != operation.getBaseRevision()) {
            throw new RequestConflictException("PREVIEW_STALE", "Event result data changed after preview");
        }

        List<Long> scopeRaceIds = operation.getRaces().stream()
                .map(Race::getId)
                .sorted()
                .toList();
        if (scopeRaceIds.isEmpty()) {
            throw new RequestConflictException("PREVIEW_STALE", "Import operation has no Race scope");
        }
        ImportPreviewDatabaseSnapshot snapshot = snapshotLoader.load(eventId);
        ImportPreviewPlan plan = planner.plan(
                eventId,
                operation.getMode(),
                scopeRaceIds,
                prepared.fileSha256(),
                operation.getSourceFilename(),
                prepared.parsed(),
                prepared.validationErrors(),
                snapshot
        );
        if (!operation.getPlanDigest().equals(plan.planDigest())) {
            throw new RequestConflictException("PREVIEW_STALE", "Import plan differs from the confirmed preview");
        }
        if (plan.blockingErrorsPresent()) {
            throw new RequestConflictException("PREVIEW_BLOCKED", "Import plan contains blocking rows");
        }
        int applicableCount = switch (operation.getMode()) {
            case ADD_NEW -> plan.totals().newCount();
            case UPDATE_EXISTING -> plan.totals().changedCount();
            case EMERGENCY_REPLACE -> plan.emergencySummary().totals().retireCount()
                    + plan.emergencySummary().totals().insertCount();
        };
        if (applicableCount == 0) {
            throw new RequestConflictException(
                    "NO_APPLICABLE_CHANGES", operation.getMode() + " plan contains no applicable rows"
            );
        }

        Map<Integer, TimingResultImportRow> sourceByRow = prepared.parsed().rows().stream()
                .collect(Collectors.toMap(
                        TimingResultImportRow::sourceRowNumber,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        Map<Long, Race> racesById = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(eventId).stream()
                .collect(Collectors.toMap(Race::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        if (!racesById.keySet().containsAll(scopeRaceIds)) {
            throw new RequestConflictException("PREVIEW_STALE", "Import Race scope changed after preview");
        }
        ImportPreviewDecision applicableDecision = operation.getMode() == ImportOperationMode.UPDATE_EXISTING
                ? ImportPreviewDecision.EXISTING_CHANGED
                : ImportPreviewDecision.NEW;
        List<ImportPreviewResponseDto.Row> applicableRows = plan.rows().stream()
                .filter(row -> row.decision() == applicableDecision)
                .toList();

        int importedSourceRows = operation.getMode() == ImportOperationMode.EMERGENCY_REPLACE
                ? plan.emergencySummary().totals().insertCount()
                : applicableCount;
        ImportBatch batch = createBatch(
                event, operation, scopeRaceIds, racesById, prepared.parsed().totalRows(), importedSourceRows
        );
        Map<CategoryKey, Category> categories = ensureSourceCategories(
                eventId, applicableRows, sourceByRow, racesById
        );
        Map<Long, List<Category>> categoriesByRace = categories.values().stream()
                .collect(Collectors.groupingBy(
                        category -> category.getRace().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, AgeCalculationMode> ageModes = awardPolicyRepository.findAllByRaceEventId(eventId).stream()
                .collect(Collectors.toMap(
                        policy -> policy.getRace().getId(),
                        AwardPolicy::getAgeCalculationMode,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<StartCluster> clusters = clusterRepository
                .findAllByRaceEventIdOrderByRaceDisplayOrderAscDisplayOrderAscIdAsc(eventId);

        Instant appliedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        EmergencyOutcome emergency = operation.getMode() == ImportOperationMode.EMERGENCY_REPLACE
                ? emergencyReplace(operation, scopeRaceIds, appliedAt, actor)
                : EmergencyOutcome.empty();
        MutationOutcome outcome = switch (operation.getMode()) {
            case ADD_NEW, EMERGENCY_REPLACE -> insertNew(
                    event, batch, applicableRows, sourceByRow, racesById, clusters, categoriesByRace, ageModes
            );
            case UPDATE_EXISTING -> updateExisting(
                    event, batch, applicableRows, sourceByRow, racesById, clusters, categoriesByRace, ageModes
            );
        };

        List<ImportOperationItem> auditItems = new ArrayList<>(emergency.auditItems());
        auditItems.addAll(plan.rows().stream()
                .map(row -> auditItem(operation, row, racesById, outcome))
                .toList());
        operationItemRepository.saveAllAndFlush(auditItems);

        batch.setStatus(ImportBatchStatus.SUCCEEDED);
        batch.setFinishedAt(appliedAt);
        importBatchRepository.save(batch);
        long newRevision = mutationGuard.bump(event);

        operation.setImportBatch(batch);
        operation.setAppliedBy(actor);
        operation.setAppliedAt(appliedAt);
        operation.setInsertedCount(outcome.insertedCount());
        operation.setUpdatedCount(outcome.updatedCount());
        operation.setResultCreatedCount(outcome.resultCreatedCount());
        operation.setNewSkippedCount(
                operation.getMode() == ImportOperationMode.UPDATE_EXISTING ? plan.totals().newCount() : 0
        );
        operation.setExistingSkippedCount(operation.getMode() == ImportOperationMode.ADD_NEW
                ? plan.totals().unchangedCount() + plan.totals().changedCount()
                : plan.totals().unchangedCount());
        operation.setUnchangedCount(plan.totals().unchangedCount());
        operation.setOutOfScopeCount(plan.totals().outOfScopeCount());
        operation.setRetiredCount(emergency.retiredCount());
        operation.setArchivedIssueCount(emergency.archivedIssueCount());
        operation.setNewRevision(newRevision);
        operation.setStatus(ImportOperationStatus.APPLIED);
        operation.setUpdatedAt(appliedAt);
        operationRepository.saveAndFlush(operation);

        LOGGER.info(
                "Applied import operation {} for event {} by {}: mode={}, inserted={}, updated={}, "
                        + "resultCreated={}, newSkipped={}, existingSkipped={}, outOfScope={}, revision={}",
                operationId, eventId, actor, operation.getMode(), operation.getInsertedCount(),
                operation.getUpdatedCount(), operation.getResultCreatedCount(), operation.getNewSkippedCount(),
                operation.getExistingSkippedCount(), operation.getOutOfScopeCount(), newRevision
        );
        return appliedResponse(operation);
    }

    private MutationOutcome insertNew(
            Event event,
            ImportBatch batch,
            List<ImportPreviewResponseDto.Row> rows,
            Map<Integer, TimingResultImportRow> sourceByRow,
            Map<Long, Race> racesById,
            List<StartCluster> clusters,
            Map<Long, List<Category>> categoriesByRace,
            Map<Long, AgeCalculationMode> ageModes
    ) {
        Map<Integer, Registration> registrationsByRow = new LinkedHashMap<>();
        List<Registration> registrations = new ArrayList<>(rows.size());
        for (ImportPreviewResponseDto.Row planned : rows) {
            TimingResultImportRow source = requireSource(sourceByRow, planned.sourceRowNumber());
            Race race = requireRace(racesById, planned);
            Registration registration = new Registration();
            registration.setRace(race);
            registration.setCluster(resolveCluster(source, race, clusters));
            registration.setImportBatch(batch);
            registration.setEntryKind(source.entryKind());
            registration.setBib(planned.bib());
            registration.setDisplayName(ImportDisplayName.from(source));
            registration.setFirstName(source.firstName());
            registration.setLastName(source.lastName());
            registration.setBirthDate(source.birthDate());
            registration.setSourceCategory(source.category());
            registration.setGender(source.gender());
            registration.setSourceRowNumber(source.sourceRowNumber());
            registration.setSourceRowHash(source.sourceRowHash());
            registration.setCategory(ageCategoryResolver.resolve(
                    source.birthDate(),
                    source.gender(),
                    source.category(),
                    event.getStartsAt(),
                    event.getTimeZone(),
                    ageModes.getOrDefault(race.getId(), AgeCalculationMode.EVENT_DATE),
                    categoriesByRace.getOrDefault(race.getId(), List.of())
            ));
            registrations.add(registration);
            registrationsByRow.put(planned.sourceRowNumber(), registration);
        }
        registrationRepository.saveAllAndFlush(registrations);

        Map<Integer, Result> resultsByRow = new LinkedHashMap<>();
        List<Result> results = new ArrayList<>(rows.size());
        for (ImportPreviewResponseDto.Row planned : rows) {
            TimingResultImportRow source = requireSource(sourceByRow, planned.sourceRowNumber());
            Result result = new Result();
            result.setRegistration(registrationsByRow.get(planned.sourceRowNumber()));
            result.setStatus(source.status());
            result.setGunTime(source.gunTime());
            result.setChipTime(source.chipTime());
            result.setOverallPlace(source.overallPlace());
            result.setGenderPlace(source.genderPlace());
            result.setCategoryPlace(source.categoryPlace());
            result.setNetOverallPlace(source.netOverallPlace());
            result.setNetGenderPlace(source.netGenderPlace());
            result.setNetCategoryPlace(source.netCategoryPlace());
            results.add(result);
            resultsByRow.put(planned.sourceRowNumber(), result);
        }
        resultRepository.saveAllAndFlush(results);
        return new MutationOutcome(registrationsByRow, resultsByRow, rows.size(), 0, 0);
    }

    private MutationOutcome updateExisting(
            Event event,
            ImportBatch batch,
            List<ImportPreviewResponseDto.Row> rows,
            Map<Integer, TimingResultImportRow> sourceByRow,
            Map<Long, Race> racesById,
            List<StartCluster> clusters,
            Map<Long, List<Category>> categoriesByRace,
            Map<Long, AgeCalculationMode> ageModes
    ) {
        List<Long> registrationIds = rows.stream()
                .map(ImportPreviewResponseDto.Row::matchedRegistrationId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Registration> registrationsById = registrationRepository.findAllById(registrationIds).stream()
                .collect(Collectors.toMap(
                        Registration::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new
                ));
        if (registrationsById.size() != registrationIds.size()) {
            throw new RequestConflictException("PREVIEW_STALE", "Matched registrations changed after preview");
        }
        Map<Long, Result> resultsByRegistrationId = resultRepository.findAllByRegistrationIdIn(registrationIds).stream()
                .collect(Collectors.toMap(
                        result -> result.getRegistration().getId(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        Map<Integer, Registration> registrationsByRow = new LinkedHashMap<>();
        Map<Integer, Result> resultsByRow = new LinkedHashMap<>();
        List<Registration> changedRegistrations = new ArrayList<>(rows.size());
        List<Result> changedResults = new ArrayList<>(rows.size());
        int resultCreatedCount = 0;

        for (ImportPreviewResponseDto.Row planned : rows) {
            TimingResultImportRow source = requireSource(sourceByRow, planned.sourceRowNumber());
            Registration registration = registrationsById.get(planned.matchedRegistrationId());
            if (registration == null) {
                throw new RequestConflictException("PREVIEW_STALE", "Matched registration changed after preview");
            }
            Race targetRace = requireRace(racesById, planned);
            StartCluster targetCluster = resolveUpdateCluster(source, targetRace, clusters, registration);
            boolean categoryInputsChanged = !registration.getRace().getId().equals(targetRace.getId())
                    || changes(registration.getGender(), source.genderSource())
                    || changes(registration.getBirthDate(), source.birthDateSource())
                    || changes(registration.getSourceCategory(), source.categorySource())
                    || planned.diffs().stream().anyMatch(diff -> diff.field().equals("categoryDefinition"));

            registration.setFirstName(apply(registration.getFirstName(), source.firstNameSource()));
            registration.setLastName(apply(registration.getLastName(), source.lastNameSource()));
            registration.setGender(apply(registration.getGender(), source.genderSource()));
            registration.setBirthDate(apply(registration.getBirthDate(), source.birthDateSource()));
            registration.setSourceCategory(apply(registration.getSourceCategory(), source.categorySource()));
            if (!source.firstNameSource().isAbsent() || !source.lastNameSource().isAbsent()) {
                registration.setDisplayName(displayName(
                        registration.getFirstName(), registration.getLastName(), registration.getBib()
                ));
            }
            registration.setRace(targetRace);
            registration.setCluster(targetCluster);
            if (categoryInputsChanged) {
                registration.setCategory(ageCategoryResolver.resolve(
                        registration.getBirthDate(),
                        registration.getGender(),
                        registration.getSourceCategory(),
                        event.getStartsAt(),
                        event.getTimeZone(),
                        ageModes.getOrDefault(targetRace.getId(), AgeCalculationMode.EVENT_DATE),
                        categoriesByRace.getOrDefault(targetRace.getId(), List.of())
                ));
            }
            registration.setImportBatch(batch);
            registration.setSourceRowNumber(source.sourceRowNumber());
            registration.setSourceRowHash(source.sourceRowHash());
            changedRegistrations.add(registration);
            registrationsByRow.put(planned.sourceRowNumber(), registration);

            Result result = resultsByRegistrationId.get(registration.getId());
            if (result == null) {
                result = new Result();
                result.setRegistration(registration);
                resultCreatedCount++;
            }
            applyResult(result, source);
            changedResults.add(result);
            resultsByRow.put(planned.sourceRowNumber(), result);
        }

        registrationRepository.saveAllAndFlush(changedRegistrations);
        resultRepository.saveAllAndFlush(changedResults);
        return new MutationOutcome(registrationsByRow, resultsByRow, 0, rows.size(), resultCreatedCount);
    }

    private ImportBatch createBatch(
            Event event,
            ImportOperation operation,
            List<Long> scopeRaceIds,
            Map<Long, Race> racesById,
            int totalRows,
            int appliedRows
    ) {
        ImportBatch batch = new ImportBatch();
        batch.setEvent(event);
        if (scopeRaceIds.size() == 1) {
            batch.setScopeType(ImportScopeType.RACE);
            batch.setRace(racesById.get(scopeRaceIds.getFirst()));
        } else {
            batch.setScopeType(ImportScopeType.EVENT);
        }
        batch.setSourceFilename(operation.getSourceFilename());
        batch.setFileSha256(operation.getFileSha256());
        batch.setStatus(ImportBatchStatus.VALIDATING);
        batch.setTotalRows(totalRows);
        batch.setImportedRows(appliedRows);
        batch.setSkippedRows(totalRows - appliedRows);
        batch.setFailedRows(0);
        batch.setStartedAt(Instant.now());
        return importBatchRepository.saveAndFlush(batch);
    }

    private Map<CategoryKey, Category> ensureSourceCategories(
            Long eventId,
            List<ImportPreviewResponseDto.Row> applicableRows,
            Map<Integer, TimingResultImportRow> sources,
            Map<Long, Race> races
    ) {
        Map<CategoryKey, Category> categories = new LinkedHashMap<>();
        categoryRepository.findAllByRaceEventId(eventId).forEach(category -> categories.put(
                new CategoryKey(category.getRace().getId(), category.getSourceName()), category
        ));
        Map<Long, Integer> nextOrder = new LinkedHashMap<>();
        categories.values().forEach(category -> nextOrder.merge(
                category.getRace().getId(), category.getDisplayOrder() + 1, Math::max
        ));
        List<Category> created = new ArrayList<>();
        for (ImportPreviewResponseDto.Row planned : applicableRows) {
            TimingResultImportRow source = requireSource(sources, planned.sourceRowNumber());
            if (!source.categorySource().hasValue()) continue;
            boolean categoryCreationPlanned = planned.diffs().stream()
                    .anyMatch(diff -> diff.field().equals("categoryDefinition"));
            if (!categoryCreationPlanned) continue;
            Race race = requireRace(races, planned);
            CategoryKey key = new CategoryKey(race.getId(), source.category());
            if (categories.containsKey(key)) continue;
            Category category = new Category();
            category.setRace(race);
            category.setSourceName(source.category());
            category.setDisplayName(source.category());
            category.setDisplayOrder(nextOrder.getOrDefault(race.getId(), 0));
            nextOrder.put(race.getId(), category.getDisplayOrder() + 1);
            categories.put(key, category);
            created.add(category);
        }
        if (!created.isEmpty()) {
            categoryRepository.saveAllAndFlush(created);
        }
        return categories;
    }

    private static StartCluster resolveCluster(
            TimingResultImportRow source,
            Race race,
            List<StartCluster> clusters
    ) {
        List<SourceField<String>> fields = List.of(
                source.clusterCodeSource(), source.clusterNameSource(), source.clusterSourceNameSource()
        );
        if (fields.stream().noneMatch(SourceField::hasValue)) return null;
        List<StartCluster> matches = clusters.stream()
                .filter(cluster -> cluster.getRace().getId().equals(race.getId()))
                .filter(cluster -> matches(source.clusterCodeSource(), cluster.getCode())
                        && matches(source.clusterNameSource(), cluster.getDisplayName())
                        && matches(source.clusterSourceNameSource(), cluster.getSourceName()))
                .toList();
        if (matches.size() != 1) {
            throw new RequestConflictException("PREVIEW_STALE", "Start cluster resolution changed after preview");
        }
        return matches.getFirst();
    }

    private static StartCluster resolveUpdateCluster(
            TimingResultImportRow source,
            Race targetRace,
            List<StartCluster> clusters,
            Registration registration
    ) {
        List<SourceField<String>> fields = List.of(
                source.clusterCodeSource(), source.clusterNameSource(), source.clusterSourceNameSource()
        );
        if (fields.stream().anyMatch(SourceField::hasValue)) {
            return resolveCluster(source, targetRace, clusters);
        }
        if (fields.stream().allMatch(SourceField::isAbsent)) {
            return registration.getCluster();
        }
        return null;
    }

    private static void applyResult(Result result, TimingResultImportRow source) {
        result.setStatus(source.status());
        result.setGunTime(apply(result.getGunTime(), source.gunTimeSource()));
        result.setChipTime(apply(result.getChipTime(), source.chipTimeSource()));
        result.setOverallPlace(apply(result.getOverallPlace(), source.overallPlaceSource()));
        result.setGenderPlace(apply(result.getGenderPlace(), source.genderPlaceSource()));
        result.setCategoryPlace(apply(result.getCategoryPlace(), source.categoryPlaceSource()));
        result.setNetOverallPlace(apply(result.getNetOverallPlace(), source.netOverallPlaceSource()));
        result.setNetGenderPlace(apply(result.getNetGenderPlace(), source.netGenderPlaceSource()));
        result.setNetCategoryPlace(apply(result.getNetCategoryPlace(), source.netCategoryPlaceSource()));
    }

    private static <T> T apply(T current, SourceField<T> source) {
        return source.isAbsent() ? current : source.valueOrNull();
    }

    private static <T> boolean changes(T current, SourceField<T> source) {
        return !source.isAbsent() && !Objects.equals(current, source.valueOrNull());
    }

    private static String displayName(String firstName, String lastName, String bib) {
        if (firstName != null && lastName != null) return firstName + " " + lastName;
        if (firstName != null) return firstName;
        if (lastName != null) return lastName;
        return "Bib " + bib;
    }

    private static boolean matches(SourceField<String> source, String current) {
        return !source.hasValue() || Objects.equals(source.value(), current);
    }

    private ImportOperationItem auditItem(
            ImportOperation operation,
            ImportPreviewResponseDto.Row row,
            Map<Long, Race> races,
            MutationOutcome outcome
    ) {
        ImportOperationItem item = new ImportOperationItem();
        item.setOperation(operation);
        item.setSourceRowNumber(row.sourceRowNumber());
        item.setBib(row.bib());
        item.setDecision(row.decision());
        item.setAction(row.futureAction());
        Registration affectedRegistration = outcome.registrationsByRow().get(row.sourceRowNumber());
        Result affectedResult = outcome.resultsByRow().get(row.sourceRowNumber());
        if (affectedRegistration != null) {
            item.setRegistrationId(affectedRegistration.getId());
            item.setResultId(affectedResult == null ? null : affectedResult.getId());
        } else if (row.matchedRegistrationId() != null) {
            item.setRegistrationId(row.matchedRegistrationId());
            item.setResultId(row.matchedResultId());
        }
        item.setTargetRace(races.get(row.targetRace().raceId()));
        return item;
    }

    private static TimingResultImportRow requireSource(
            Map<Integer, TimingResultImportRow> sourceByRow,
            int sourceRowNumber
    ) {
        TimingResultImportRow source = sourceByRow.get(sourceRowNumber);
        if (source == null) throw new RequestConflictException("PREVIEW_STALE", "Source row mapping changed");
        return source;
    }

    private static Race requireRace(Map<Long, Race> races, ImportPreviewResponseDto.Row row) {
        if (row.targetRace() == null) {
            throw new RequestConflictException("PREVIEW_STALE", "Target Race mapping changed");
        }
        Race race = races.get(row.targetRace().raceId());
        if (race == null) throw new RequestConflictException("PREVIEW_STALE", "Target Race mapping changed");
        return race;
    }

    private static ImportApplyResponseDto appliedResponse(ImportOperation operation) {
        if (operation.getImportBatch() == null || operation.getInsertedCount() == null
                || operation.getExistingSkippedCount() == null || operation.getOutOfScopeCount() == null
                || operation.getNewRevision() == null || operation.getAppliedAt() == null) {
            throw new IllegalStateException("Applied import operation has incomplete result metadata");
        }
        return new ImportApplyResponseDto(
                operation.getId(), operation.getStatus(), operation.getMode(), operation.getEvent().getId(),
                operation.getImportBatch().getId(), operation.getInsertedCount(),
                operation.getUpdatedCount(), operation.getResultCreatedCount(), operation.getNewSkippedCount(),
                operation.getExistingSkippedCount(), operation.getUnchangedCount(), operation.getOutOfScopeCount(),
                operation.getRetiredCount(), operation.getArchivedIssueCount(),
                operation.getNewRevision(), operation.getAppliedAt()
        );
    }

    private EmergencyOutcome emergencyReplace(
            ImportOperation operation,
            List<Long> scopeRaceIds,
            Instant appliedAt,
            String actor
    ) {
        List<Registration> registrations = registrationRepository.findAllCurrentByRaceIds(scopeRaceIds);
        List<Long> registrationIds = registrations.stream().map(Registration::getId).toList();
        Map<Long, Result> resultsByRegistration = resultRepository.findAllByRegistrationIdIn(registrationIds).stream()
                .collect(Collectors.toMap(
                        result -> result.getRegistration().getId(),
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        List<ResultIssueRequest> issues = issueRepository.findActiveByEventAndRaceScope(
                operation.getEvent().getId(), scopeRaceIds, ResultIssueStatus.activeStatuses()
        );
        int archived = 0;
        for (ResultIssueRequest issue : issues) {
            if (issueLifecycleService.archive(
                    issue,
                    ResultIssueArchiveReason.EMERGENCY_REPLACEMENT,
                    operation.getId(),
                    actor,
                    appliedAt
            )) {
                archived++;
            }
        }

        List<ImportOperationItem> retirementItems = new ArrayList<>(registrations.size());
        for (Registration registration : registrations) {
            registration.setRetiredAt(appliedAt);
            registration.setRetiredByImportOperationId(operation.getId());
            Result result = resultsByRegistration.get(registration.getId());
            ImportOperationItem item = new ImportOperationItem();
            item.setOperation(operation);
            item.setSourceRowNumber(null);
            item.setBib(registration.getBib());
            item.setDecision(ImportPreviewDecision.RETIRED);
            item.setAction(ImportPreviewAction.RETIRE);
            item.setRegistrationId(registration.getId());
            item.setResultId(result == null ? null : result.getId());
            item.setTargetRace(registration.getRace());
            retirementItems.add(item);
        }
        registrationRepository.saveAllAndFlush(registrations);
        issueRepository.flush();
        return new EmergencyOutcome(retirementItems, registrations.size(), archived);
    }

    private record CategoryKey(Long raceId, String sourceName) {
    }

    private record MutationOutcome(
            Map<Integer, Registration> registrationsByRow,
            Map<Integer, Result> resultsByRow,
            int insertedCount,
            int updatedCount,
            int resultCreatedCount
    ) {
    }

    private record EmergencyOutcome(
            List<ImportOperationItem> auditItems,
            int retiredCount,
            int archivedIssueCount
    ) {
        private static EmergencyOutcome empty() { return new EmergencyOutcome(List.of(), 0, 0); }
    }
}
