package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.ImportBatch;
import ru.sportsresults.domain.ImportBatchStatus;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.importing.TimingResultImportRow;
import ru.sportsresults.repository.CategoryRepository;
import ru.sportsresults.repository.ImportBatchRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RegistrationRepository;
import ru.sportsresults.repository.ResultRepository;
import ru.sportsresults.repository.SnapshotCleanupRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EventSnapshotWriter {

    private final RaceRepository raceRepository;
    private final CategoryRepository categoryRepository;
    private final ImportBatchRepository importBatchRepository;
    private final RegistrationRepository registrationRepository;
    private final ResultRepository resultRepository;
    private final SnapshotCleanupRepository cleanupRepository;
    private final AgeCategoryRecalculationService recalculationService;
    private final EventResultDataMutationGuard mutationGuard;

    public EventSnapshotWriter(
            RaceRepository raceRepository,
            CategoryRepository categoryRepository,
            ImportBatchRepository importBatchRepository,
            RegistrationRepository registrationRepository,
            ResultRepository resultRepository,
            SnapshotCleanupRepository cleanupRepository,
            AgeCategoryRecalculationService recalculationService,
            EventResultDataMutationGuard mutationGuard
    ) {
        this.raceRepository = raceRepository;
        this.categoryRepository = categoryRepository;
        this.importBatchRepository = importBatchRepository;
        this.registrationRepository = registrationRepository;
        this.resultRepository = resultRepository;
        this.cleanupRepository = cleanupRepository;
        this.recalculationService = recalculationService;
        this.mutationGuard = mutationGuard;
    }

    @Transactional
    public ImportBatch replaceEventSnapshot(Long eventId, Long batchId, List<TimingResultImportRow> rows) {
        Event event = mutationGuard.lock(eventId);
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalStateException("Import batch disappeared: " + batchId));
        if (!batch.getEvent().getId().equals(eventId)) {
            throw new IllegalStateException("Import batch belongs to another event");
        }

        Map<String, Race> races = ensureRaces(event, rows);
        ensureCategories(eventId, rows, races);

        cleanupRepository.deleteEventSnapshot(eventId);

        List<Registration> registrations = new ArrayList<>(rows.size());
        for (TimingResultImportRow row : rows) {
            Registration registration = new Registration();
            Race race = races.get(row.raceCode());
            registration.setRace(race);
            registration.setCategory(null);
            registration.setImportBatch(batch);
            registration.setEntryKind(row.entryKind());
            registration.setBib(row.bib());
            registration.setDisplayName(ImportDisplayName.from(row));
            registration.setFirstName(row.firstName());
            registration.setLastName(row.lastName());
            registration.setBirthDate(row.birthDate());
            registration.setSourceCategory(row.category());
            registration.setGender(row.gender());
            registration.setSourceRowNumber(row.sourceRowNumber());
            registration.setSourceRowHash(row.sourceRowHash());
            registrations.add(registration);
        }
        registrationRepository.saveAllAndFlush(registrations);
        recalculationService.recalculateEvent(eventId);

        List<Result> results = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            TimingResultImportRow row = rows.get(index);
            Result result = new Result();
            result.setRegistration(registrations.get(index));
            result.setStatus(row.status());
            result.setGunTime(row.gunTime());
            result.setChipTime(row.chipTime());
            result.setOverallPlace(row.overallPlace());
            result.setGenderPlace(row.genderPlace());
            result.setCategoryPlace(row.categoryPlace());
            result.setNetOverallPlace(row.netOverallPlace());
            result.setNetGenderPlace(row.netGenderPlace());
            result.setNetCategoryPlace(row.netCategoryPlace());
            results.add(result);
        }
        resultRepository.saveAllAndFlush(results);

        batch.setStatus(ImportBatchStatus.SUCCEEDED);
        batch.setTotalRows(rows.size());
        batch.setImportedRows(rows.size());
        batch.setSkippedRows(0);
        batch.setFailedRows(0);
        batch.setErrorSummary(null);
        batch.setFinishedAt(Instant.now());
        ImportBatch completed = importBatchRepository.saveAndFlush(batch);
        mutationGuard.bump(event);
        return completed;
    }

    private Map<String, Race> ensureRaces(Event event, List<TimingResultImportRow> rows) {
        List<Race> existing = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId());
        Map<String, Race> races = new LinkedHashMap<>();
        existing.forEach(race -> races.put(race.getSourceCode(), race));
        int nextOrder = existing.stream().mapToInt(Race::getDisplayOrder).max().orElse(-1) + 1;
        List<Race> created = new ArrayList<>();
        for (TimingResultImportRow row : rows) {
            Race race = races.get(row.raceCode());
            if (race == null) {
                race = new Race();
                race.setEvent(event);
                race.setSourceCode(row.raceCode());
                race.setName(row.raceCode());
                race.setSlug(slugFor(row.raceCode()));
                race.setDisplayOrder(nextOrder++);
                races.put(row.raceCode(), race);
                created.add(race);
            }
        }
        raceRepository.saveAll(created);
        raceRepository.flush();
        return races;
    }

    private Map<CategoryKey, Category> ensureCategories(
            Long eventId,
            List<TimingResultImportRow> rows,
            Map<String, Race> races
    ) {
        Map<CategoryKey, Category> categories = new LinkedHashMap<>();
        categoryRepository.findAllByRaceEventId(eventId).forEach(category -> categories.put(
                new CategoryKey(category.getRace().getId(), category.getSourceName()),
                category
        ));
        Map<Long, Integer> nextOrders = new LinkedHashMap<>();
        categories.values().forEach(category -> nextOrders.merge(
                category.getRace().getId(),
                category.getDisplayOrder() + 1,
                Math::max
        ));
        List<Category> created = new ArrayList<>();
        for (TimingResultImportRow row : rows) {
            if (row.category() == null) {
                continue;
            }
            Race race = races.get(row.raceCode());
            CategoryKey key = new CategoryKey(race.getId(), row.category());
            if (!categories.containsKey(key)) {
                Category category = new Category();
                category.setRace(race);
                category.setSourceName(row.category());
                category.setDisplayName(row.category());
                int order = nextOrders.getOrDefault(race.getId(), 0);
                category.setDisplayOrder(order);
                nextOrders.put(race.getId(), order + 1);
                categories.put(key, category);
                created.add(category);
            }
        }
        categoryRepository.saveAll(created);
        categoryRepository.flush();
        return categories;
    }

    private static String slugFor(String sourceCode) {
        String base = sourceCode.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) {
            base = "race";
        }
        String suffix = sha256(sourceCode).substring(0, 10);
        int maximumBaseLength = 160 - suffix.length() - 1;
        if (base.length() > maximumBaseLength) {
            base = base.substring(0, maximumBaseLength).replaceAll("-$", "");
        }
        return base + "-" + suffix;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record CategoryKey(Long raceId, String sourceName) {
    }
}
