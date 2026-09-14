package ru.sportsresults.service;

import org.springframework.stereotype.Component;
import ru.sportsresults.api.dto.CategoryDto;
import ru.sportsresults.api.dto.AdminResultDetailsDto;
import ru.sportsresults.api.dto.ResultDetailsDto;
import ru.sportsresults.api.dto.ResultListItemDto;
import ru.sportsresults.api.dto.RankingAchievementDto;
import ru.sportsresults.api.dto.SplitDto;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.domain.Split;
import ru.sportsresults.repository.ResultListProjection;

import java.time.Duration;
import java.util.List;

@Component
public class ResultDtoMapper {

    private final PublicCategoryPresentation categoryPresentation;

    public ResultDtoMapper(PublicCategoryPresentation categoryPresentation) {
        this.categoryPresentation = categoryPresentation;
    }

    public ResultListItemDto toListItem(ResultListProjection projection, boolean publicPresentation) {
        return toListItem(projection, publicPresentation, null, List.of());
    }

    public ResultListItemDto toListItem(
            ResultListProjection projection,
            boolean publicPresentation,
            RankingBasis rankingBasisOverride
    ) {
        return toListItem(projection, publicPresentation, rankingBasisOverride, List.of());
    }

    public ResultListItemDto toListItem(
            ResultListProjection projection,
            boolean publicPresentation,
            RankingBasis rankingBasisOverride,
            List<RankingAchievementDto> rankingAchievements
    ) {
        return toListItem(projection, publicPresentation, rankingBasisOverride, rankingAchievements, null);
    }

    public ResultListItemDto toListItem(
            ResultListProjection projection,
            boolean publicPresentation,
            RankingBasis rankingBasisOverride,
            List<RankingAchievementDto> rankingAchievements,
            Integer displayPosition
    ) {
        RankingBasis effectiveBasis = rankingBasisOverride == null ? projection.rankingBasis() : rankingBasisOverride;
        CategoryDto category = projection.categoryId() == null
                ? null
                : new CategoryDto(
                        projection.categoryId(),
                        publicPresentation
                                ? categoryPresentation.publicName(projection.categorySourceName(), projection.categoryName())
                                : projection.categoryName()
                );
        return new ResultListItemDto(
                projection.registrationId(),
                projection.resultId(),
                projection.raceId(),
                projection.raceName(),
                projection.displayName(),
                projection.firstName(),
                projection.lastName(),
                projection.bib(),
                projection.gender(),
                publicPresentation ? null : projection.sourceCategory(),
                publicPresentation ? null : projection.clusterId(),
                publicPresentation ? null : projection.clusterName(),
                publicPresentation ? null : projection.entryKind(),
                category,
                projection.status(),
                milliseconds(projection.gunTime()),
                milliseconds(projection.chipTime()),
                projection.overallPlace(),
                projection.genderPlace(),
                projection.categoryPlace(),
                projection.netOverallPlace(),
                projection.netGenderPlace(),
                projection.netCategoryPlace(),
                effectiveBasis == RankingBasis.NONE ? null : projection.contextualPlace(),
                displayPosition,
                effectiveBasis.name(),
                rankingAchievements
        );
    }

    public ResultDetailsDto toPublicDetails(
            Result result,
            List<Split> splits,
            RankingBasis rankingBasis,
            List<RankingAchievementDto> rankingAchievements
    ) {
        Registration registration = result.getRegistration();
        Category category = registration.getCategory();
        return new ResultDetailsDto(
                registration.getId(),
                result.getId(),
                registration.getRace().getEvent().getId(),
                registration.getRace().getId(),
                registration.getRace().getName(),
                registration.getDisplayName(),
                registration.getFirstName(),
                registration.getLastName(),
                registration.getBib(),
                registration.getGender(),
                registration.getEntryKind(),
                category == null ? null : new CategoryDto(
                        category.getId(),
                        categoryPresentation.publicName(category)
                ),
                result.getStatus(),
                milliseconds(result.getGunTime()),
                milliseconds(result.getChipTime()),
                result.getOverallPlace(),
                result.getGenderPlace(),
                result.getCategoryPlace(),
                result.getNetOverallPlace(),
                result.getNetGenderPlace(),
                result.getNetCategoryPlace(),
                rankingBasis.name(),
                rankingAchievements,
                splits.stream().map(ResultDtoMapper::toSplit).toList()
        );
    }

    public AdminResultDetailsDto toAdminDetails(Result result, List<Split> splits) {
        Registration registration = result.getRegistration();
        Category category = registration.getCategory();
        return new AdminResultDetailsDto(
                registration.getId(),
                result.getId(),
                registration.getRace().getEvent().getId(),
                registration.getRace().getId(),
                registration.getRace().getName(),
                registration.getDisplayName(),
                registration.getFirstName(),
                registration.getLastName(),
                registration.getBirthDate(),
                registration.getSourceCategory(),
                registration.getBib(),
                registration.getGender(),
                registration.getEntryKind(),
                registration.getCluster() == null ? null : registration.getCluster().getId(),
                registration.getCluster() == null ? null : registration.getCluster().getDisplayName(),
                category == null ? null : new CategoryDto(category.getId(), category.getDisplayName()),
                result.getStatus(),
                milliseconds(result.getGunTime()),
                milliseconds(result.getChipTime()),
                result.getOverallPlace(),
                result.getGenderPlace(),
                result.getCategoryPlace(),
                result.getNetOverallPlace(),
                result.getNetGenderPlace(),
                result.getNetCategoryPlace(),
                List.of(),
                splits.stream().map(ResultDtoMapper::toSplit).toList()
        );
    }

    private static SplitDto toSplit(Split split) {
        return new SplitDto(
                split.getCheckpoint().getId(),
                split.getCheckpoint().getCode(),
                split.getCheckpoint().getName(),
                split.getCheckpoint().getSequenceNumber(),
                split.getCheckpoint().getDistanceMeters(),
                milliseconds(split.getGunTime()),
                milliseconds(split.getChipTime()),
                split.getOverallPlace(),
                split.getGenderPlace(),
                split.getCategoryPlace(),
                split.getNetOverallPlace(),
                split.getNetGenderPlace(),
                split.getNetCategoryPlace()
        );
    }

    private static Long milliseconds(Duration duration) {
        return duration == null ? null : duration.toMillis();
    }
}
