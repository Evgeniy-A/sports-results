package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.AwardPolicy;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.repository.AwardPolicyRepository;
import ru.sportsresults.repository.CategoryRepository;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RegistrationRepository;
import ru.sportsresults.repository.ResultRepository;
import ru.sportsresults.repository.StartClusterRepository;
import ru.sportsresults.repository.ResultIssueRequestRepository;
import ru.sportsresults.domain.ResultIssueStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImportPreviewSnapshotLoader {

    private final EventRepository eventRepository;
    private final RaceRepository raceRepository;
    private final StartClusterRepository clusterRepository;
    private final CategoryRepository categoryRepository;
    private final AwardPolicyRepository awardPolicyRepository;
    private final RegistrationRepository registrationRepository;
    private final ResultRepository resultRepository;
    private final ResultIssueRequestRepository issueRepository;

    public ImportPreviewSnapshotLoader(
            EventRepository eventRepository,
            RaceRepository raceRepository,
            StartClusterRepository clusterRepository,
            CategoryRepository categoryRepository,
            AwardPolicyRepository awardPolicyRepository,
            RegistrationRepository registrationRepository,
            ResultRepository resultRepository,
            ResultIssueRequestRepository issueRepository
    ) {
        this.eventRepository = eventRepository;
        this.raceRepository = raceRepository;
        this.clusterRepository = clusterRepository;
        this.categoryRepository = categoryRepository;
        this.awardPolicyRepository = awardPolicyRepository;
        this.registrationRepository = registrationRepository;
        this.resultRepository = resultRepository;
        this.issueRepository = issueRepository;
    }

    @Transactional(readOnly = true)
    public ImportPreviewDatabaseSnapshot load(Long eventId) {
        var event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found"));

        Map<Long, AgeCalculationMode> ageModes = awardPolicyRepository.findAllByRaceEventId(eventId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        policy -> policy.getRace().getId(),
                        AwardPolicy::getAgeCalculationMode,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<ImportPreviewDatabaseSnapshot.RaceSnapshot> races = raceRepository
                .findAllByEventIdOrderByDisplayOrderAsc(eventId).stream()
                .map(race -> new ImportPreviewDatabaseSnapshot.RaceSnapshot(
                        race.getId(),
                        race.getSourceCode(),
                        race.getName(),
                        race.getSportFormat().getId(),
                        race.getSportFormat().getDisplayName(),
                        ageModes.getOrDefault(race.getId(), AgeCalculationMode.EVENT_DATE),
                        race.getResultsPublicationStatus()
                ))
                .toList();
        Map<Long, ImportPreviewDatabaseSnapshot.RaceSnapshot> racesById = new LinkedHashMap<>();
        races.forEach(race -> racesById.put(race.id(), race));

        List<ImportPreviewDatabaseSnapshot.ClusterSnapshot> clusters = clusterRepository
                .findAllByRaceEventIdOrderByRaceDisplayOrderAscDisplayOrderAscIdAsc(eventId).stream()
                .map(cluster -> new ImportPreviewDatabaseSnapshot.ClusterSnapshot(
                        cluster.getId(),
                        cluster.getRace().getId(),
                        cluster.getCode(),
                        cluster.getSourceName(),
                        cluster.getDisplayName()
                ))
                .toList();
        Map<Long, ImportPreviewDatabaseSnapshot.ClusterSnapshot> clustersById = new LinkedHashMap<>();
        clusters.forEach(cluster -> clustersById.put(cluster.id(), cluster));

        List<ImportPreviewDatabaseSnapshot.CategorySnapshot> categories = categoryRepository
                .findAllByRaceEventId(eventId).stream()
                .map(ImportPreviewSnapshotLoader::category)
                .toList();
        Map<Long, ImportPreviewDatabaseSnapshot.CategorySnapshot> categoriesById = new LinkedHashMap<>();
        categories.forEach(category -> categoriesById.put(category.id(), category));

        List<Registration> registrations = registrationRepository.findAllCurrentByEventId(eventId);
        Map<Long, Result> resultsByRegistration = resultRepository.findAllByRegistrationIdIn(
                        registrations.stream().map(Registration::getId).toList()
                ).stream()
                .collect(java.util.stream.Collectors.toMap(
                        result -> result.getRegistration().getId(),
                        result -> result,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<ImportPreviewDatabaseSnapshot.RegistrationSnapshot> registrationSnapshots = registrations.stream()
                .map(registration -> registration(registration, racesById, clustersById,
                        categoriesById,
                        resultsByRegistration.get(registration.getId())))
                .toList();

        List<ImportPreviewDatabaseSnapshot.ActiveIssueSnapshot> activeIssues = races.isEmpty() ? List.of() : issueRepository
                .findActiveByEventAndRaceScope(
                        eventId,
                        races.stream().map(ImportPreviewDatabaseSnapshot.RaceSnapshot::id).toList(),
                        ResultIssueStatus.activeStatuses()
                ).stream()
                .map(issue -> new ImportPreviewDatabaseSnapshot.ActiveIssueSnapshot(
                        issue.getId(), issue.getRegistration().getId(), issue.getRegistration().getRace().getId()
                ))
                .sorted(java.util.Comparator.comparing(ImportPreviewDatabaseSnapshot.ActiveIssueSnapshot::id))
                .toList();

        return new ImportPreviewDatabaseSnapshot(
                event.getResultDataRevision(), races, clusters, registrationSnapshots,
                event.getStartsAt(), event.getTimeZone(), categories,
                event.getName(), event.getLocation(), activeIssues
        );
    }

    private static ImportPreviewDatabaseSnapshot.RegistrationSnapshot registration(
            Registration registration,
            Map<Long, ImportPreviewDatabaseSnapshot.RaceSnapshot> races,
            Map<Long, ImportPreviewDatabaseSnapshot.ClusterSnapshot> clusters,
            Map<Long, ImportPreviewDatabaseSnapshot.CategorySnapshot> categories,
            Result result
    ) {
        return new ImportPreviewDatabaseSnapshot.RegistrationSnapshot(
                registration.getId(),
                registration.getBib(),
                registration.getDisplayName(),
                registration.getFirstName(),
                registration.getLastName(),
                registration.getGender(),
                registration.getBirthDate(),
                registration.getSourceCategory(),
                races.get(registration.getRace().getId()),
                registration.getCluster() == null ? null : clusters.get(registration.getCluster().getId()),
                registration.getCategory() == null ? null : categories.get(registration.getCategory().getId()),
                result == null ? null : result(result)
        );
    }

    private static ImportPreviewDatabaseSnapshot.CategorySnapshot category(Category category) {
        return new ImportPreviewDatabaseSnapshot.CategorySnapshot(
                category.getId(),
                category.getRace().getId(),
                category.getSourceName(),
                category.getDisplayName(),
                category.getDisplayOrder(),
                category.getMinAge(),
                category.getMaxAge(),
                category.getGender(),
                category.isEnabled()
        );
    }

    private static ImportPreviewDatabaseSnapshot.ResultSnapshot result(Result result) {
        return new ImportPreviewDatabaseSnapshot.ResultSnapshot(
                result.getId(),
                result.getStatus(),
                result.getGunTime(),
                result.getChipTime(),
                result.getOverallPlace(),
                result.getGenderPlace(),
                result.getCategoryPlace(),
                result.getNetOverallPlace(),
                result.getNetGenderPlace(),
                result.getNetCategoryPlace()
        );
    }
}
