package ru.sportsresults.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.PageResponse;
import ru.sportsresults.api.dto.AdminResultDetailsDto;
import ru.sportsresults.api.dto.RankingAchievementDto;
import ru.sportsresults.api.dto.ResultDetailsDto;
import ru.sportsresults.api.dto.ResultListItemDto;
import ru.sportsresults.domain.EventPublicationStatus;
import ru.sportsresults.domain.ResultsPublicationStatus;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.domain.PublicResultVisibility;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.AwardPolicyRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.ResultListProjection;
import ru.sportsresults.repository.ResultRepository;
import ru.sportsresults.repository.ResultSearchCriteria;
import ru.sportsresults.repository.ResultSortField;
import ru.sportsresults.repository.PublicCategoryProtocolRepository;
import ru.sportsresults.repository.SplitRepository;
import ru.sportsresults.repository.StartClusterRepository;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ResultQueryService {

    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 200;

    private final EventRepository eventRepository;
    private final RaceRepository raceRepository;
    private final ResultRepository resultRepository;
    private final SplitRepository splitRepository;
    private final ResultDtoMapper mapper;
    private final OfficialRankingService officialRankingService;
    private final StartClusterRepository startClusterRepository;
    private final AwardPolicyRepository awardPolicyRepository;
    private final PublicCategoryProtocolRepository publicCategoryProtocolRepository;

    public ResultQueryService(
            EventRepository eventRepository,
            RaceRepository raceRepository,
            ResultRepository resultRepository,
            SplitRepository splitRepository,
            ResultDtoMapper mapper,
            OfficialRankingService officialRankingService,
            StartClusterRepository startClusterRepository,
            AwardPolicyRepository awardPolicyRepository,
            PublicCategoryProtocolRepository publicCategoryProtocolRepository
    ) {
        this.eventRepository = eventRepository;
        this.raceRepository = raceRepository;
        this.resultRepository = resultRepository;
        this.splitRepository = splitRepository;
        this.mapper = mapper;
        this.officialRankingService = officialRankingService;
        this.startClusterRepository = startClusterRepository;
        this.awardPolicyRepository = awardPolicyRepository;
        this.publicCategoryProtocolRepository = publicCategoryProtocolRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ResultListItemDto> search(
            Long eventId,
            Long raceId,
            String name,
            String bib,
            String gender,
            Long categoryId,
            Long clusterId,
            String status,
            int page,
            int size,
            String sort,
            String direction
    ) {
        requirePublishedEvent(eventId);
        if (raceId == null) {
            throw new InvalidRequestException("RACE_REQUIRED", "raceId is required for the public results protocol");
        }
        Race race = requirePublicRace(eventId, raceId);
        if (clusterId != null) {
            if (startClusterRepository.findByIdAndRaceId(clusterId, raceId).isEmpty()) {
                throw new ResourceNotFoundException("START_CLUSTER_NOT_FOUND", "Start cluster not found in this race");
            }
        }
        validatePublicStatus(status);
        String publicStatus = status != null && status.equalsIgnoreCase("all") ? null : status;
        validatePage(page, size);
        RankingBasis officialBasis = officialBasis(race);
        ResultSortField sortField = parsePublicSort(sort, officialBasis);
        Sort.Direction sortDirection = parseDirection(direction);
        List<Long> excludedResultIds = categoryId == null
                ? List.of()
                : publicCategoryProtocolRepository.findExcludedPrimaryPrizeWinnerIds(eventId, raceId, categoryId);

        Page<ResultListProjection> resultPage = resultRepository.search(
                new ResultSearchCriteria(
                        eventId, raceId, name, bib, gender, categoryId, clusterId, publicStatus,
                        officialBasis, true, excludedResultIds
                ),
                page,
                size,
                sortField,
                sortDirection
        );
        Map<Long, List<RankingAchievementDto>> achievements =
                officialRankingService.findForPage(eventId, resultPage.getContent());
        List<ResultListProjection> projections = resultPage.getContent();
        List<ResultListItemDto> items = new java.util.ArrayList<>(projections.size());
        for (int index = 0; index < projections.size(); index++) {
            ResultListProjection item = projections.get(index);
            Integer displayPosition = officialBasis == RankingBasis.NONE && isFinished(item.status())
                    ? Math.toIntExact(Math.addExact(Math.multiplyExact((long) page, size), index + 1L))
                    : null;
            items.add(mapper.toListItem(
                    item,
                    true,
                    officialBasis,
                    achievements.getOrDefault(item.resultId(), List.of()),
                    displayPosition
            ));
        }
        return new PageResponse<>(
                items,
                page,
                size,
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                apiSortName(sortField),
                sortDirection.name().toLowerCase(Locale.ROOT)
        );
    }

    public PageResponse<ResultListItemDto> search(
            Long eventId, Long raceId, String name, String bib, String gender, Long categoryId, String status,
            int page, int size, String sort, String direction
    ) {
        return search(eventId, raceId, name, bib, gender, categoryId, null, status, page, size, sort, direction);
    }

    @Transactional(readOnly = true)
    public PageResponse<ResultListItemDto> searchAdmin(
            Long eventId,
            Long raceId,
            String name,
            String bib,
            String gender,
            Long categoryId,
            Long clusterId,
            String status,
            int page,
            int size,
            String sort,
            String direction,
            RankingBasis rankingBasis
    ) {
        if (!eventRepository.existsById(eventId)) {
            throw new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found");
        }
        if (raceId != null) requireRace(eventId, raceId);
        validatePage(page, size);
        ResultSortField sortField = parseSort(sort);
        Sort.Direction sortDirection = parseDirection(direction);
        RankingBasis resolvedBasis = rankingBasis == null ? RankingBasis.CHIP_TIME : rankingBasis;
        Page<ResultListProjection> resultPage = resultRepository.search(
                new ResultSearchCriteria(
                        eventId, raceId, name, bib, gender, categoryId, clusterId, status,
                        resolvedBasis, false, List.of()
                ),
                page,
                size,
                sortField,
                sortDirection
        );
        return new PageResponse<>(
                resultPage.getContent().stream()
                        .map(item -> mapper.toListItem(item, false, resolvedBasis))
                        .toList(),
                page,
                size,
                resultPage.getTotalElements(),
                resultPage.getTotalPages(),
                apiSortName(sortField),
                sortDirection.name().toLowerCase(Locale.ROOT)
        );
    }

    public PageResponse<ResultListItemDto> searchAdmin(
            Long eventId,
            Long raceId,
            String name,
            String bib,
            String gender,
            Long categoryId,
            String status,
            int page,
            int size,
            String sort,
            String direction,
            RankingBasis rankingBasis
    ) {
        return searchAdmin(
                eventId, raceId, name, bib, gender, categoryId, null, status,
                page, size, sort, direction, rankingBasis
        );
    }

    /**
     * Explicit ranking is an administrative/query-test capability. Public controllers never call this overload.
     */
    public PageResponse<ResultListItemDto> search(
            Long eventId,
            Long raceId,
            String name,
            String bib,
            String gender,
            Long categoryId,
            String status,
            int page,
            int size,
            String sort,
            String direction,
            RankingBasis rankingBasis
    ) {
        return searchAdmin(
                eventId, raceId, name, bib, gender, categoryId, status,
                page, size, sort, direction, rankingBasis
        );
    }

    @Transactional(readOnly = true)
    public ResultDetailsDto getPublishedResult(Long resultId) {
        Result result = resultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("RESULT_NOT_FOUND", "Result not found"));
        if (!PublicResultVisibility.isResultPublic(result)) {
            throw new ResourceNotFoundException("RESULT_NOT_FOUND", "Published result not found");
        }
        RankingBasis rankingBasis = officialBasis(result.getRegistration().getRace());
        return mapper.toPublicDetails(
                result,
                splitRepository.findAllByResultIdOrderByCheckpointSequenceNumberAsc(resultId),
                rankingBasis,
                officialRankingService.findForResult(
                        result.getRegistration().getRace().getEvent().getId(),
                        result.getRegistration().getRace().getId(),
                        resultId
                )
        );
    }

    @Transactional(readOnly = true)
    public AdminResultDetailsDto getAdminResult(Long resultId) {
        Result result = resultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("RESULT_NOT_FOUND", "Result not found"));
        return mapper.toAdminDetails(
                result,
                splitRepository.findAllByResultIdOrderByCheckpointSequenceNumberAsc(resultId)
        );
    }

    private void requirePublishedEvent(Long eventId) {
        if (eventRepository.findByIdAndPublicationStatus(eventId, EventPublicationStatus.PUBLISHED)
                .filter(event -> event.getEventSeries().isActive())
                .isEmpty()) {
            throw new ResourceNotFoundException("EVENT_NOT_FOUND", "Published event not found");
        }
    }

    private Race requireRace(Long eventId, Long raceId) {
        return raceRepository.findByIdAndEventId(raceId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found in this event"));
    }

    private Race requirePublicRace(Long eventId, Long raceId) {
        return raceRepository
                .findByIdAndEventIdAndPublicVisibleTrueAndResultsPublicationStatus(
                        raceId, eventId, ResultsPublicationStatus.PUBLISHED
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "RACE_NOT_FOUND", "Race not found in this event"));
    }

    private RankingBasis officialBasis(Race race) {
        return awardPolicyRepository.findByRaceId(race.getId())
                .map(ru.sportsresults.domain.AwardPolicy::getRankingBasis)
                .orElse(race.getPublicRankingBasis());
    }

    private static void validatePublicStatus(String status) {
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("all") && !isPublicStatus(status)) {
            throw new InvalidRequestException(
                    "INVALID_PUBLIC_STATUS",
                    "Public status filter supports only finished or disqualified"
            );
        }
    }

    private static boolean isPublicStatus(String status) {
        return PublicResultVisibility.isPublicStatus(status);
    }

    private static boolean isFinished(String status) {
        return status != null && status.equalsIgnoreCase("finished");
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("INVALID_PAGE", "page must be greater than or equal to zero");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "INVALID_PAGE_SIZE",
                    "size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
    }

    private static ResultSortField parseSort(String value) {
        String normalized = value == null ? "place" : value.strip();
        return switch (normalized) {
            case "place" -> ResultSortField.CONTEXT_PLACE;
            case "time" -> ResultSortField.CONTEXT_TIME;
            case "overallPlace" -> ResultSortField.OVERALL_PLACE;
            case "netOverallPlace" -> ResultSortField.NET_OVERALL_PLACE;
            case "gunTime" -> ResultSortField.GUN_TIME;
            case "chipTime" -> ResultSortField.CHIP_TIME;
            case "displayName" -> ResultSortField.DISPLAY_NAME;
            case "bib" -> ResultSortField.BIB;
            default -> throw new InvalidRequestException("INVALID_SORT", "Unsupported sort field: " + normalized);
        };
    }

    private static ResultSortField parsePublicSort(String value, RankingBasis rankingBasis) {
        ResultSortField field = parseSort(value);
        if (rankingBasis == RankingBasis.NONE && field == ResultSortField.CONTEXT_PLACE) {
            return ResultSortField.GUN_TIME;
        }
        return field;
    }

    private static Sort.Direction parseDirection(String value) {
        return Sort.Direction.fromOptionalString(value == null ? "asc" : value)
                .orElseThrow(() -> new InvalidRequestException(
                        "INVALID_SORT_DIRECTION",
                        "direction must be asc or desc"
                ));
    }

    private static String apiSortName(ResultSortField field) {
        return switch (field) {
            case CONTEXT_PLACE -> "place";
            case CONTEXT_TIME -> "time";
            case OVERALL_PLACE -> "overallPlace";
            case NET_OVERALL_PLACE -> "netOverallPlace";
            case GUN_TIME -> "gunTime";
            case CHIP_TIME -> "chipTime";
            case DISPLAY_NAME -> "displayName";
            case BIB -> "bib";
        };
    }
}
