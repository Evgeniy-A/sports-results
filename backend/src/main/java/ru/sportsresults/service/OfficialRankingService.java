package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.api.dto.RankingAchievementDto;
import ru.sportsresults.domain.RankingAchievementType;
import ru.sportsresults.repository.OfficialRankingProjection;
import ru.sportsresults.repository.OfficialRankingQueryRepository;
import ru.sportsresults.repository.ResultListProjection;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OfficialRankingService {

    private final OfficialRankingQueryRepository repository;
    private final PublicCategoryPresentation categoryPresentation;

    public OfficialRankingService(
            OfficialRankingQueryRepository repository,
            PublicCategoryPresentation categoryPresentation
    ) {
        this.repository = repository;
        this.categoryPresentation = categoryPresentation;
    }

    public Map<Long, List<RankingAchievementDto>> findForPage(
            Long eventId,
            Collection<ResultListProjection> results
    ) {
        return find(
                eventId,
                results.stream().map(ResultListProjection::raceId).distinct().toList(),
                results.stream().map(ResultListProjection::resultId).toList()
        );
    }

    public List<RankingAchievementDto> findForResult(Long eventId, Long raceId, Long resultId) {
        return find(eventId, List.of(raceId), List.of(resultId)).getOrDefault(resultId, List.of());
    }

    private Map<Long, List<RankingAchievementDto>> find(
            Long eventId,
            Collection<Long> raceIds,
            Collection<Long> resultIds
    ) {
        Map<Long, List<RankingAchievementDto>> grouped = new LinkedHashMap<>();
        for (OfficialRankingProjection projection : repository.findAchievements(eventId, raceIds, resultIds)) {
            grouped.computeIfAbsent(projection.resultId(), ignored -> new java.util.ArrayList<>())
                    .add(toDto(projection));
        }
        grouped.replaceAll((ignored, achievements) -> List.copyOf(achievements));
        return Map.copyOf(grouped);
    }

    private RankingAchievementDto toDto(OfficialRankingProjection projection) {
        String code;
        String name;
        String labelName;
        if (projection.type() == RankingAchievementType.ABSOLUTE) {
            code = null;
            name = "Абсолют";
            labelName = "абсолют";
        } else if (projection.type() == RankingAchievementType.GENDER) {
            code = projection.gender() == null ? null : projection.gender().toLowerCase(Locale.ROOT);
            name = genderName(projection.gender());
            labelName = name.toLowerCase(Locale.forLanguageTag("ru"));
        } else {
            code = projection.categorySourceName();
            name = categoryPresentation.publicName(
                    projection.categorySourceName(), projection.categoryDisplayName()
            );
            labelName = name;
        }
        return new RankingAchievementDto(
                projection.place(),
                projection.type(),
                code,
                name,
                projection.place() + " место · " + labelName,
                projection.prize()
        );
    }

    private static String genderName(String gender) {
        if (gender == null) return "Половой зачёт";
        if (gender.equalsIgnoreCase("female")) return "Женщины";
        if (gender.equalsIgnoreCase("male")) return "Мужчины";
        return gender;
    }
}
