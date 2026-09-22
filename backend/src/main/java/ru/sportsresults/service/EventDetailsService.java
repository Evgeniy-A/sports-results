package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.*;
import ru.sportsresults.domain.*;
import ru.sportsresults.repository.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EventDetailsService {
    private final EventRepository eventRepository;
    private final EventParticipantInfoRepository participantInfoRepository;
    private final EventInfoBlockRepository infoBlockRepository;
    private final EventScheduleItemRepository scheduleRepository;
    private final EventDocumentRepository documentRepository;
    private final RaceRepository raceRepository;
    private final StartClusterRepository clusterRepository;
    private final AwardPolicyRepository awardPolicyRepository;
    private final CategoryRepository categoryRepository;

    public EventDetailsService(EventRepository eventRepository,
                               EventParticipantInfoRepository participantInfoRepository,
                               EventInfoBlockRepository infoBlockRepository,
                               EventScheduleItemRepository scheduleRepository,
                               EventDocumentRepository documentRepository,
                               RaceRepository raceRepository,
                               StartClusterRepository clusterRepository,
                               AwardPolicyRepository awardPolicyRepository,
                               CategoryRepository categoryRepository) {
        this.eventRepository = eventRepository;
        this.participantInfoRepository = participantInfoRepository;
        this.infoBlockRepository = infoBlockRepository;
        this.scheduleRepository = scheduleRepository;
        this.documentRepository = documentRepository;
        this.raceRepository = raceRepository;
        this.clusterRepository = clusterRepository;
        this.awardPolicyRepository = awardPolicyRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public EventDetailsDto getPublished(Long eventId) {
        Event event = eventRepository.findByIdAndPublicationStatus(eventId, EventPublicationStatus.PUBLISHED)
                .filter(candidate -> candidate.getEventSeries().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Published event not found"));
        return toDto(event);
    }

    @Transactional(readOnly = true)
    public EventDetailsDto getPublished(String slug) {
        Event event = eventRepository.findBySlugAndPublicationStatus(slug, EventPublicationStatus.PUBLISHED)
                .filter(candidate -> candidate.getEventSeries().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Published event not found"));
        return toDto(event);
    }

    private EventDetailsDto toDto(Event event) {
        Long eventId = event.getId();
        List<Race> races = raceRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId);
        Map<Long, AwardPolicy> policies = awardPolicyRepository.findAllByRaceEventId(eventId).stream()
                .collect(Collectors.toMap(policy -> policy.getRace().getId(), Function.identity()));
        Map<Long, List<Category>> categories = categoryRepository.findAllByRaceEventId(eventId).stream()
                .filter(Category::isEnabled)
                .collect(Collectors.groupingBy(category -> category.getRace().getId()));
        Map<Long, List<StartCluster>> clusters = clusterRepository
                .findAllByRaceEventIdOrderByRaceDisplayOrderAscDisplayOrderAscIdAsc(eventId).stream()
                .collect(Collectors.groupingBy(cluster -> cluster.getRace().getId()));

        List<PublicRaceDto> flatRaces = races.stream()
                .filter(Race::isPublicVisible)
                .map(race -> {
                    AwardPolicy policy = policies.get(race.getId());
                    RaceRulesSummaryDto rules = policy == null
                            ? null
                            : rules(policy, categories.getOrDefault(race.getId(), List.of()));
                    return new PublicRaceDto(
                            race.getId(), race.getName(), race.getSlug(), race.getDistanceMeters(),
                            race.getStartsAt(), race.getDisplayOrder(), true,
                            clusters.getOrDefault(race.getId(), List.of()).stream()
                                    .map(StartClusterService::toDto).toList(),
                            rules, race.getResultsPublicationStatus(),
                            race.getResultsPublicationStatus() == ResultsPublicationStatus.PUBLISHED
                    );
                })
                .toList();
        boolean anyPublishedResults = flatRaces.stream().anyMatch(PublicRaceDto::resultsPublished);
        ResultsPublicationStatus publicResultsStatus = anyPublishedResults
                ? ResultsPublicationStatus.PUBLISHED
                : ResultsPublicationStatus.DRAFT;

        return new EventDetailsDto(eventId, event.getEventSeries().getId(), event.getEventSeries().getName(),
                event.getEventSeries().getSlug(), event.getName(), event.getSlug(), event.getStartsAt(), event.getEndsAt(),
                event.getLocation(), event.getTimeZone(), EventPhaseCalculator.calculate(event, Instant.now()),
                event.getPublicationStatus(), publicResultsStatus, anyPublishedResults,
                participantInfoRepository.findByEventId(eventId).map(EventContentService::toDto).orElse(null),
                scheduleRepository.findAllByEventIdOrderByStartsAtAscDisplayOrderAscIdAsc(eventId).stream()
                        .map(EventContentService::toDto).toList(),
                infoBlockRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId).stream()
                        .map(EventContentService::toDto).toList(),
                documentRepository.findAllByEventIdAndPublicDocumentTrueOrderByDisplayOrderAscIdAsc(eventId).stream()
                        .map(EventDocumentService::toDto).toList(),
                flatRaces);
    }

    private static RaceRulesSummaryDto rules(AwardPolicy policy, List<Category> categories) {
        boolean enabled = policy.isCategoryEnabled();
        List<CategoryRuleDto> categoryRules = enabled ? categories.stream()
                .sorted(java.util.Comparator.comparingInt(Category::getDisplayOrder).thenComparing(Category::getDisplayName))
                .map(category -> new CategoryRuleDto(category.getId(), category.getDisplayName(), category.getMinAge(),
                        category.getMaxAge(), category.getGender(), category.getDisplayOrder()))
                .toList() : List.of();
        return new RaceRulesSummaryDto(policy.getRankingBasis(), policy.getPrimaryStandingMode(),
                policy.getAbsolutePrizePlaces(), enabled,
                enabled ? policy.getCategoryPrizePlaces() : null,
                enabled ? policy.isExcludeAbsoluteWinnersFromCategory() : null,
                enabled ? policy.getAgeCalculationMode() : null,
                categoryRules);
    }
}
