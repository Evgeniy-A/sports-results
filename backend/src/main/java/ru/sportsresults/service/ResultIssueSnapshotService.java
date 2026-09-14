package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.api.dto.RankingAchievementDto;
import ru.sportsresults.domain.AwardPolicy;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.ImportBatch;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.domain.ResultIssueSnapshotOrigin;
import ru.sportsresults.domain.SportFormat;
import ru.sportsresults.repository.AwardPolicyRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
public class ResultIssueSnapshotService {

    private final AwardPolicyRepository awardPolicyRepository;
    private final OfficialRankingService officialRankingService;
    private final ObjectMapper objectMapper;

    public ResultIssueSnapshotService(
            AwardPolicyRepository awardPolicyRepository,
            OfficialRankingService officialRankingService,
            ObjectMapper objectMapper
    ) {
        this.awardPolicyRepository = awardPolicyRepository;
        this.officialRankingService = officialRankingService;
        this.objectMapper = objectMapper;
    }

    public void capture(ResultIssueRequest issue) {
        Event event = issue.getEvent();
        Registration registration = issue.getRegistration();
        Race race = registration.getRace();
        SportFormat format = race.getSportFormat();
        Category category = registration.getCategory();
        ImportBatch importBatch = registration.getImportBatch();
        Result result = issue.getResult();

        issue.setSnapshotOrigin(ResultIssueSnapshotOrigin.CAPTURED_AT_CREATION);
        issue.setSnapshotEventName(event.getName());
        issue.setSnapshotEventLocation(event.getLocation());
        issue.setSnapshotEventStartsAt(event.getStartsAt());
        issue.setSnapshotSportFormatId(format.getId());
        issue.setSnapshotSportFormatName(format.getDisplayName());
        issue.setSnapshotSportFormatCode(format.getCode());
        issue.setSnapshotRaceId(race.getId());
        issue.setSnapshotRaceName(race.getName());
        issue.setSnapshotRaceCode(race.getSourceCode());
        issue.setSnapshotRaceDistanceMeters(race.getDistanceMeters());
        issue.setSnapshotBib(registration.getBib());
        issue.setSnapshotDisplayName(registration.getDisplayName());
        issue.setSnapshotEffectiveCategoryName(category == null ? null : category.getDisplayName());
        issue.setSnapshotSourceCategory(registration.getSourceCategory());
        issue.setSnapshotCategoryPubliclyEnabled(awardPolicyRepository.findByRaceId(race.getId())
                .map(AwardPolicy::isCategoryEnabled)
                .orElse(false));
        issue.setSnapshotRanking(writeRanking(event, race, result));
        issue.setSnapshotImportBatchId(importBatch == null ? null : importBatch.getId());
        issue.setSnapshotSourceRowNumber(registration.getSourceRowNumber());
    }

    private String writeRanking(Event event, Race race, Result result) {
        List<RankingAchievementDto> achievements = result == null
                ? List.of()
                : officialRankingService.findForResult(event.getId(), race.getId(), result.getId());
        try {
            return objectMapper.writeValueAsString(achievements);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not capture result issue ranking snapshot", exception);
        }
    }
}
