package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.sportsresults.api.dto.RankingAchievementDto;
import ru.sportsresults.domain.*;
import ru.sportsresults.repository.*;
import ru.sportsresults.storage.FileStorageService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CertificateServiceTest {
    @Test
    void mapsOfficialAndImportedValuesWithoutCreatingParallelRankingRules() {
        AwardPolicyRepository policies = mock(AwardPolicyRepository.class);
        OfficialRankingService rankings = mock(OfficialRankingService.class);
        CertificateService service = service(policies, rankings);
        Event event = new Event(); ReflectionTestUtils.setField(event, "id", 7L);
        Race race = new Race(); ReflectionTestUtils.setField(race, "id", 8L); race.setEvent(event); race.setPublicRankingBasis(RankingBasis.CHIP_TIME);
        Registration registration = new Registration(); registration.setRace(race); registration.setDisplayName("Иван Петров"); registration.setBib("A-17");
        Category category = new Category(); category.setSourceName("M35"); category.setDisplayName("М35–39"); registration.setCategory(category);
        Result result = new Result(); ReflectionTestUtils.setField(result, "id", 9L); result.setRegistration(registration);
        result.setGunTime(Duration.ofSeconds(2600)); result.setChipTime(Duration.ofSeconds(2500));
        result.setOverallPlace(11); result.setGenderPlace(7); result.setCategoryPlace(3);
        AwardPolicy policy = new AwardPolicy(); policy.setRankingBasis(RankingBasis.GUN_TIME);
        when(policies.findByRaceId(8L)).thenReturn(Optional.of(policy));
        when(rankings.findForResult(7L, 8L, 9L)).thenReturn(List.of(
                new RankingAchievementDto(5, RankingAchievementType.GENDER, "male", "Мужчины", "5 место · мужчины", false),
                new RankingAchievementDto(2, RankingAchievementType.CATEGORY, "M35", "М35–39", "2 место · М35–39", true)
        ));

        Map<CertificateField, String> values = service.certificateValues(result);
        assertThat(values.get(CertificateField.OFFICIAL_TIME)).isEqualTo("00:43:20");
        assertThat(values.get(CertificateField.GUN_TIME)).isEqualTo("00:43:20");
        assertThat(values.get(CertificateField.CHIP_TIME)).isEqualTo("00:41:40");
        assertThat(values.get(CertificateField.OFFICIAL_PLACE)).isEqualTo("5 место");
        assertThat(values.get(CertificateField.OVERALL_PLACE)).isEqualTo("11 место");
        assertThat(values.get(CertificateField.GENDER_PLACE)).isEqualTo("7 место");
        assertThat(values.get(CertificateField.CATEGORY_PLACE)).isEqualTo("3 место");
        assertThat(values.get(CertificateField.CATEGORY_NAME)).isEqualTo("М35–39");
        assertThat(values.get(CertificateField.BIB)).isEqualTo("A-17");
    }

    @Test
    void officialTimeAndPlaceAreUnavailableForNoneWithoutPrimaryAchievement() {
        AwardPolicyRepository policies = mock(AwardPolicyRepository.class);
        OfficialRankingService rankings = mock(OfficialRankingService.class);
        CertificateService service = service(policies, rankings);
        Event event = new Event(); ReflectionTestUtils.setField(event, "id", 1L);
        Race race = new Race(); ReflectionTestUtils.setField(race, "id", 2L); race.setEvent(event);
        Registration registration = new Registration(); registration.setRace(race); registration.setDisplayName("Команда Тест");
        Result result = new Result(); ReflectionTestUtils.setField(result, "id", 3L); result.setRegistration(registration); result.setChipTime(Duration.ofMinutes(10));
        AwardPolicy policy = new AwardPolicy(); policy.setRankingBasis(RankingBasis.NONE);
        when(policies.findByRaceId(2L)).thenReturn(Optional.of(policy));
        when(rankings.findForResult(1L, 2L, 3L)).thenReturn(List.of());
        Map<CertificateField, String> values = service.certificateValues(result);
        assertThat(values.get(CertificateField.OFFICIAL_TIME)).isNull();
        assertThat(values.get(CertificateField.OFFICIAL_PLACE)).isNull();
        assertThat(values.get(CertificateField.CHIP_TIME)).isEqualTo("00:10:00");
    }

    private static CertificateService service(AwardPolicyRepository policies, OfficialRankingService rankings) {
        return new CertificateService(mock(EventService.class), mock(CertificateSettingsRepository.class),
                mock(CertificateTemplateRepository.class), mock(RaceRepository.class), mock(ResultRepository.class),
                policies, rankings, new PublicCategoryPresentation(), mock(FileStorageService.class),
                mock(CertificateTemplateCache.class), mock(CertificateGenerationGuard.class),
                mock(CertificatePdfRenderer.class), mock(AdminChangeLogRepository.class), 1024, 2048);
    }
}
