package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.EventPublicationStatus;
import ru.sportsresults.domain.ResultsPublicationStatus;
import ru.sportsresults.domain.RaceEntryMode;
import ru.sportsresults.domain.EventDocumentType;
import ru.sportsresults.domain.EventSeries;
import ru.sportsresults.domain.ImportBatchStatus;
import ru.sportsresults.domain.PrimaryStandingMode;
import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.api.dto.UpdateAwardPolicyRequest;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.EventSeriesRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RegistrationRepository;
import ru.sportsresults.repository.ResultRepository;
import ru.sportsresults.repository.SportFormatRepository;
import ru.sportsresults.service.AwardPolicyService;
import ru.sportsresults.service.ImportService;
import ru.sportsresults.service.EventContentService;
import ru.sportsresults.service.EventDocumentService;
import ru.sportsresults.service.EventService;
import ru.sportsresults.service.RaceAdminService;
import ru.sportsresults.service.RaceResultsPublicationService;
import ru.sportsresults.service.StartClusterService;
import ru.sportsresults.service.SportFormatService;
import ru.sportsresults.api.dto.*;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicitly launched fixture server for non-destructive browser checks; excluded from normal test discovery.
 */
class BrowserE2eServer {

    @Test
    void serveIsolatedBrowserFixturesUntilStopMarkerAppears() throws Exception {
        Path stopMarker = Path.of("target", "browser-e2e.stop").toAbsolutePath().normalize();
        Files.deleteIfExists(stopMarker);
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            String jdbcUrl;
            String username;
            try (Connection connection = dataSource.getConnection()) {
                jdbcUrl = connection.getMetaData().getURL();
                username = connection.getMetaData().getUserName();
            }
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                    SportsResultsApplication.class,
                    BrowserE2eAttachmentStorageConfiguration.class
            )
                    .web(WebApplicationType.SERVLET)
                    .run(
                            "--server.port=8082",
                            "--spring.datasource.url=" + jdbcUrl,
                            "--spring.datasource.username=" + username,
                            "--spring.datasource.password=",
                            "--app.admin.username=e2e-admin",
                            "--app.admin.password=e2e-secret",
                            "--app.documents.storage-root=" + Path.of("target", "browser-documents").toAbsolutePath(),
                            "--app.result-issues.attachments.storage-provider=memory",
                            "--logging.level.root=WARN"
                    )) {
                seed(context);
                System.out.println("BROWSER_E2E_READY http://127.0.0.1:8082");
                long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(15);
                while (!Files.exists(stopMarker) && System.nanoTime() < deadline) {
                    Thread.sleep(250);
                }
                assertThat(Files.exists(stopMarker)).as("browser E2E stop marker").isTrue();
            } finally {
                Files.deleteIfExists(stopMarker);
            }
        }
    }

    static void seed(ConfigurableApplicationContext context) {
        EventSeriesRepository seriesRepository = context.getBean(EventSeriesRepository.class);
        EventRepository eventRepository = context.getBean(EventRepository.class);
        RaceRepository raceRepository = context.getBean(RaceRepository.class);
        ImportService importService = context.getBean(ImportService.class);
        AwardPolicyService awardPolicyService = context.getBean(AwardPolicyService.class);
        EventContentService contentService = context.getBean(EventContentService.class);
        EventDocumentService documentService = context.getBean(EventDocumentService.class);
        RaceAdminService raceAdminService = context.getBean(RaceAdminService.class);
        RaceResultsPublicationService raceResultsPublicationService =
                context.getBean(RaceResultsPublicationService.class);
        StartClusterService clusterService = context.getBean(StartClusterService.class);
        SportFormatService sportFormatService = context.getBean(SportFormatService.class);
        SportFormatRepository sportFormatRepository = context.getBean(SportFormatRepository.class);
        RegistrationRepository registrationRepository = context.getBean(RegistrationRepository.class);
        ResultRepository resultRepository = context.getBean(ResultRepository.class);
        EventService eventService = context.getBean(EventService.class);

        EventSeries series = new EventSeries();
        series.setName("Контрольный забег");
        series.setSlug("kontrolnyy-zabeg");
        series.setActive(true);
        series = seriesRepository.saveAndFlush(series);

        Event inquiryOpen = event(series, "Уточнение результатов — открыто", "result-inquiry-open", "Екатеринбург");
        inquiryOpen.setStartsAt(Instant.now().minusSeconds(172800));
        inquiryOpen.setEndsAt(Instant.now().minusSeconds(86400));
        inquiryOpen = eventRepository.saveAndFlush(inquiryOpen);
        eventService.updateResultInquirySettings(inquiryOpen.getId(), new UpdateResultInquirySettingsRequest(
                true, 30, "timing@example.test"
        ), "e2e-admin");
        assertThat(importService.importEvent(
                inquiryOpen.getId(), "browser-result-inquiry-open.csv",
                resultInquiryOpenCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        var withoutResult = registrationRepository.findAllByRaceEventIdAndBib(
                inquiryOpen.getId(), "103"
        ).getFirst();
        resultRepository.delete(resultRepository.findByRegistrationId(withoutResult.getId()).orElseThrow());
        resultRepository.flush();

        Event inquiryClosed = event(series, "Уточнение результатов — закрыто", "result-inquiry-closed", "Екатеринбург");
        inquiryClosed.setStartsAt(Instant.now().minusSeconds(61L * 86400));
        inquiryClosed.setEndsAt(Instant.now().minusSeconds(60L * 86400));
        inquiryClosed = eventRepository.saveAndFlush(inquiryClosed);
        eventService.updateResultInquirySettings(inquiryClosed.getId(), new UpdateResultInquirySettingsRequest(
                true, 30, "timing@example.test"
        ), "e2e-admin");
        assertThat(importService.importEvent(
                inquiryClosed.getId(), "browser-result-inquiry-closed.csv",
                resultInquiryClosedCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);

        Event noCategories = event(series, "Забег без категорий", "bez-kategoriy", "Екатеринбург");
        noCategories = eventRepository.saveAndFlush(noCategories);
        assertThat(importService.importEvent(
                noCategories.getId(), "browser-no-categories.csv", noCategoriesCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        var noCategoriesRace = raceRepository.findByEventIdAndSourceCode(
                noCategories.getId(), "5 км"
        ).orElseThrow();
        awardPolicyService.upsert(noCategoriesRace.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.BY_GENDER, 3, false,
                AgeCalculationMode.EVENT_DATE, 3, false
        ), "e2e-admin");

        Event m52 = event(series, "M52 2025", "m52-2025", "Екатеринбург");
        m52.setStartsAt(Instant.parse("2025-08-24T03:00:00Z"));
        m52.setEndsAt(Instant.parse("2025-08-24T12:00:00Z"));
        m52 = eventRepository.saveAndFlush(m52);
        assertThat(importService.importEvent(
                m52.getId(), "browser-m52.csv", m52Csv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        for (String sourceCode : java.util.List.of("10 km", "42.2 km")) {
            var m52Race = raceRepository.findByEventIdAndSourceCode(m52.getId(), sourceCode).orElseThrow();
            awardPolicyService.upsert(m52Race.getId(), new UpdateAwardPolicyRequest(
                    RankingBasis.GUN_TIME, PrimaryStandingMode.BY_GENDER, 3, false,
                    AgeCalculationMode.EVENT_DATE, 0, false
            ), "e2e-admin");
        }

        Event hero = event(series, "Гонка Героев 2026", "gonka-geroev-config", "Москва");
        hero = eventRepository.saveAndFlush(hero);
        assertThat(importService.importEvent(
                hero.getId(), "browser-hero-config.csv", heroConfigurationCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        var heroDefault = sportFormatRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(hero.getId()).getFirst();
        var massFormat = sportFormatService.update(hero.getId(), heroDefault.getId(),
                new UpsertSportFormatRequest("mass", "mass", "mass", 1, true), "e2e-admin");
        var teamsFormat = sportFormatService.create(hero.getId(),
                new UpsertSportFormatRequest("teams", "teams", "teams", 2, true), "e2e-admin");
        var champFormat = sportFormatService.create(hero.getId(),
                new UpsertSportFormatRequest("champ", "champ", "champ", 3, true), "e2e-admin");
        var corpFormat = sportFormatService.create(hero.getId(),
                new UpsertSportFormatRequest("corp", "corp", "corp", 4, false), "e2e-admin");
        var massRace = raceRepository.findByEventIdAndSourceCode(hero.getId(), "mass").orElseThrow();
        var teamsRace = raceRepository.findByEventIdAndSourceCode(hero.getId(), "teams").orElseThrow();
        var champRace = raceRepository.findByEventIdAndSourceCode(hero.getId(), "champ").orElseThrow();
        var corpRace = raceRepository.findByEventIdAndSourceCode(hero.getId(), "corp").orElseThrow();
        moveRace(raceAdminService, hero.getId(), massRace, massFormat.id());
        moveRace(raceAdminService, hero.getId(), teamsRace, teamsFormat.id());
        moveRace(raceAdminService, hero.getId(), champRace, champFormat.id());
        moveRace(raceAdminService, hero.getId(), corpRace, corpFormat.id());
        awardPolicyService.upsert(massRace.getId(), noStandingPolicy(), "e2e-admin");
        awardPolicyService.upsert(teamsRace.getId(), noStandingPolicy(), "e2e-admin");
        awardPolicyService.upsert(champRace.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.ALL, 0, false,
                AgeCalculationMode.EVENT_DATE, 0, false), "e2e-admin");

        Event freeTable = event(series, "Свободная таблица результатов", "svobodnaya-tablica", "Тюмень");
        freeTable = eventRepository.saveAndFlush(freeTable);
        assertThat(importService.importEvent(
                freeTable.getId(), "browser-free-table.csv", freeTableCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        var freeTableRace = raceRepository.findByEventIdAndSourceCode(
                freeTable.getId(), "Массовый старт"
        ).orElseThrow();
        awardPolicyService.upsert(freeTableRace.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.NONE, PrimaryStandingMode.NONE, 0, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), "e2e-admin");

        Event meaningful = event(series, "Забег с возрастными категориями", "vozrastnye-kategorii", "Москва");
        meaningful = eventRepository.saveAndFlush(meaningful);
        assertThat(importService.importEvent(
                meaningful.getId(), "browser-meaningful.csv", meaningfulCategoriesCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        var race = raceRepository.findByEventIdAndSourceCode(meaningful.getId(), "10 км").orElseThrow();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.BY_GENDER, 3, true,
                AgeCalculationMode.EVENT_DATE, 3, true
        ), "e2e-admin");

        Event future = event(series, "Гонка Героев 2027", "gonka-geroev-2027", "Москва");
        future.setStartsAt(Instant.parse("2027-06-15T06:00:00Z"));
        future.setEndsAt(Instant.parse("2027-06-15T15:00:00Z"));
        future.setTimeZone("Europe/Moscow");
        future.setResultsPublicationStatus(ResultsPublicationStatus.DRAFT);
        future = eventRepository.saveAndFlush(future);
        contentService.updateParticipantInfo(future.getId(), new UpdateEventParticipantInfoRequest(
                "Городской забег с несколькими стартовыми волнами.",
                "Олимпийский комплекс «Лужники»", "Москва, Лужнецкая набережная, 24",
                "Вход в стартовый городок со стороны набережной.",
                new BigDecimal("55.715600"), new BigDecimal("37.553500"),
                "Приходите минимум за 45 минут до времени своего кластера."
        ), "e2e-admin");
        contentService.createScheduleItem(future.getId(), new UpsertEventScheduleItemRequest(
                LocalDateTime.parse("2027-06-15T08:00:00"), null, "Открытие стартового городка", null, 1
        ), "e2e-admin");
        contentService.createScheduleItem(future.getId(), new UpsertEventScheduleItemRequest(
                LocalDateTime.parse("2027-06-15T09:00:00"), null, "Разминка", null, 2
        ), "e2e-admin");
        contentService.createScheduleItem(future.getId(), new UpsertEventScheduleItemRequest(
                LocalDateTime.parse("2027-06-15T13:00:00"), null, "Награждение", null, 3
        ), "e2e-admin");
        contentService.createInfoBlock(future.getId(), new UpsertEventInfoBlockRequest(
                "Получение стартового пакета", "Выдача работает в стартовом городке с 08:00.", 1
        ), "e2e-admin");
        RaceDto futureRace = raceAdminService.create(future.getId(), new UpsertRaceRequest(
                "10 km", "10 км", "10-km", new BigDecimal("10000"),
                Instant.parse("2027-06-15T06:30:00Z"), RaceEntryMode.INDIVIDUAL, 1, null, true
        ), "e2e-admin");
        awardPolicyService.upsert(futureRace.id(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.BY_GENDER, 3, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), "e2e-admin");
        clusterService.create(future.getId(), futureRace.id(), new UpsertStartClusterRequest(
                "ELITE", null, "Элитный", 1, LocalDateTime.parse("2027-06-15T09:30:00")
        ), "e2e-admin");
        clusterService.create(future.getId(), futureRace.id(), new UpsertStartClusterRequest(
                "A", null, "Кластер A", 2, LocalDateTime.parse("2027-06-15T09:40:00")
        ), "e2e-admin");
        clusterService.create(future.getId(), futureRace.id(), new UpsertStartClusterRequest(
                "B", null, "Кластер B", 3, LocalDateTime.parse("2027-06-15T09:50:00")
        ), "e2e-admin");
        documentService.upload(future.getId(), EventDocumentType.PARTICIPANT_GUIDE, "Гайд участника", 1,
                true, "guide-2027.pdf", "application/pdf",
                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII), "e2e-admin");

        Event separated = event(series, "Раздельные протоколы 2027", "razdelnye-protokoly-2027", "Казань");
        separated.setStartsAt(Instant.parse("2027-08-10T06:00:00Z"));
        separated = eventRepository.saveAndFlush(separated);
        var individual = sportFormatService.create(separated.getId(), new UpsertSportFormatRequest(
                "individual", "Individual", "Индивидуальный", 1, true), "e2e-admin");
        assertThat(importService.importEvent(
                separated.getId(), "browser-formats.csv", separatedFormatsCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        var team = sportFormatService.create(separated.getId(), new UpsertSportFormatRequest(
                "team", "Team", "Командный", 2, true), "e2e-admin");
        var raceA = raceRepository.findByEventIdAndSourceCode(separated.getId(), "Race A").orElseThrow();
        var raceB = raceRepository.findByEventIdAndSourceCode(separated.getId(), "Race B").orElseThrow();
        var raceC = raceRepository.findByEventIdAndSourceCode(separated.getId(), "Race C").orElseThrow();
        raceAdminService.update(separated.getId(), raceC.getId(), new UpsertRaceRequest(
                raceC.getSourceCode(), raceC.getName(), raceC.getSlug(), raceC.getDistanceMeters(), raceC.getStartsAt(),
                raceC.getEntryMode(), raceC.getDisplayOrder(), team.id(), true), "e2e-admin");
        assertThat(individual.id()).isNotEqualTo(team.id());
        for (var scopedRace : java.util.List.of(raceA, raceB, raceC)) {
            awardPolicyService.upsert(scopedRace.getId(), new UpdateAwardPolicyRequest(
                    RankingBasis.CHIP_TIME, PrimaryStandingMode.ALL, 1, false,
                    AgeCalculationMode.EVENT_DATE, 0, false), "e2e-admin");
        }
        raceAdminService.update(separated.getId(), raceB.getId(), new UpsertRaceRequest(
                raceB.getSourceCode(), raceB.getName(), raceB.getSlug(), raceB.getDistanceMeters(), raceB.getStartsAt(),
                raceB.getEntryMode(), raceB.getDisplayOrder(), individual.id(), false), "e2e-admin");

        Event mixedPublication = event(
                series, "Частично опубликованные протоколы", "mixed-race-publication", "Пермь");
        mixedPublication = eventRepository.saveAndFlush(mixedPublication);
        assertThat(importService.importEvent(
                mixedPublication.getId(), "browser-mixed-publication.csv",
                separatedFormatsCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        var mixedPublishedRace = raceRepository.findByEventIdAndSourceCode(
                mixedPublication.getId(), "Race A").orElseThrow();
        raceResultsPublicationService.publish(
                mixedPublication.getId(), mixedPublishedRace.getId(), "e2e-admin");

        // These pre-Stage-D browser fixtures intentionally model already-published protocols.
        // Keep the future Event in Draft and publish every imported Race explicitly via the
        // same service used by the administrative API.
        for (Event fixture : java.util.List.of(
                inquiryOpen, inquiryClosed, noCategories, m52, hero, freeTable, meaningful, separated
        )) {
            for (var fixtureRace : raceRepository.findAllByEventIdOrderByDisplayOrderAsc(fixture.getId())) {
                raceResultsPublicationService.publish(fixture.getId(), fixtureRace.getId(), "e2e-admin");
            }
        }
    }

    private static Event event(EventSeries series, String name, String slug, String location) {
        Event event = new Event();
        event.setEventSeries(series);
        event.setName(name);
        event.setSlug(slug);
        event.setLocation(location);
        event.setStartsAt(Instant.parse("2026-08-28T06:00:00Z"));
        event.setPublicationStatus(EventPublicationStatus.PUBLISHED);
        event.setResultsPublicationStatus(ResultsPublicationStatus.PUBLISHED);
        return event;
    }

    private static void moveRace(RaceAdminService service, Long eventId, ru.sportsresults.domain.Race race,
                                 Long formatId) {
        service.update(eventId, race.getId(), new UpsertRaceRequest(
                race.getSourceCode(), race.getName(), race.getSlug(), null, race.getStartsAt(),
                race.getEntryMode(), race.getDisplayOrder(), formatId, true), "e2e-admin");
    }

    private static UpdateAwardPolicyRequest noStandingPolicy() {
        return new UpdateAwardPolicyRequest(
                RankingBasis.NONE, PrimaryStandingMode.NONE, 0, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        );
    }

    private static String noCategoriesCsv() {
        return header() + """
                Анна,Финишина,female,1992-01-01,5 км,001,18+ Female,finished,1200000.0,1190000.0,1.0,1.0,1.0,1.0,1.0,1.0
                Борис,Быстрый,male,1990-01-01,5 км,A-2,18+ Male,finished,1210000.0,1180000.0,2.0,1.0,1.0,2.0,1.0,1.0
                Виктор,Снятый,male,1989-01-01,5 км,3,18+ Male,disqualified,,,,,,,,
                Галина,На дистанции,female,1995-01-01,5 км,4,18+ Female,running,,,,,,,,
                """;
    }

    private static String resultInquiryOpenCsv() {
        return header() + """
                Публичный,Финишер,male,1990-01-01,5 км,101,Open,finished,1200000.0,1190000.0,1.0,1.0,1.0,1.0,1.0,1.0
                Непубличный,Статус,female,1991-02-02,5 км,102,Open,quarantine,1210000.0,1180000.0,,,,,,
                Без,Результата,male,1992-03-03,5 км,103,Open,running,,,,,,,,
                Первый,Дубль,male,1990-01-10,5 км,1100,Open,quarantine,1220000.0,1170000.0,,,,,,
                Второй,Дубль,female,1991-02-20,5 км,1100,Open,running,,,,,,,,
                Публичный,Смешанный,male,1993-04-05,5 км,1200,Open,finished,1230000.0,1180000.0,2.0,1.0,1.0,2.0,1.0,1.0
                Непубличный,Смешанный,female,1994-05-06,5 км,1200,Open,running,,,,,,,,
                Публичный,Тройной,male,1995-06-07,5 км,1300,Open,finished,1240000.0,1190000.0,3.0,2.0,2.0,3.0,2.0,2.0
                Второй,Тройной,female,1996-07-08,5 км,1300,Open,notstarted,,,,,,,,
                Третий,Тройной,male,1997-08-09,5 км,1300,Open,quarantine,,,,,,,,
                """;
    }

    private static String resultInquiryClosedCsv() {
        return header() + """
                Закрытый,Результат,male,1990-01-01,5 км,202,Open,quarantine,1200000.0,1190000.0,,,,,,
                """;
    }

    private static String meaningfulCategoriesCsv() {
        return header() + """
                Шаров,,male,,10 км,101,18+ Male,finished,1000000.0,9000000.0,1.0,1.0,1.0,9.0,9.0,9.0
                Минеев,,male,,10 км,102,18+ Male,finished,2000000.0,8000000.0,2.0,2.0,2.0,8.0,8.0,8.0
                Лейман,,male,,10 км,103,18+ Male,finished,3000000.0,7000000.0,3.0,3.0,3.0,7.0,7.0,7.0
                Трошкин,,male,,10 км,104,18+ Male,finished,4000000.0,6000000.0,4.0,4.0,4.0,6.0,6.0,6.0
                Батыршин,,male,,10 км,105,18+ Male,finished,5000000.0,5000000.0,5.0,5.0,5.0,5.0,5.0,5.0
                Петров,,male,,10 км,106,18+ Male,finished,6000000.0,4000000.0,6.0,6.0,6.0,4.0,4.0,4.0
                Андреев,,male,,10 км,107,18+ Male,finished,7000000.0,3000000.0,7.0,7.0,7.0,3.0,3.0,3.0
                Абрамов,,male,,10 км,108,18+ Male,finished,8000000.0,2000000.0,8.0,8.0,8.0,2.0,2.0,2.0
                Шалаев,,male,,10 км,109,18+ Male,finished,9000000.0,1000000.0,9.0,9.0,9.0,1.0,1.0,1.0
                Сидор,Дисквалифицированный,male,,10 км,110,18+ Male,disqualified,500000.0,500000.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String m52Csv() {
        return header() + """
                Первый,Десять,male,1990-01-01,10 km,10-1,18+ Male,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Второй,Десять,male,1990-01-01,10 km,10-2,18+ Male,finished,2000.0,1900.0,2.0,2.0,2.0,2.0,2.0,2.0
                Третий,Десять,male,1990-01-01,10 km,10-3,18+ Male,finished,3000.0,2900.0,3.0,3.0,3.0,3.0,3.0,3.0
                Четвёртый,Десять,male,1990-01-01,10 km,10-4,18+ Male,finished,4000.0,3900.0,4.0,4.0,4.0,4.0,4.0,4.0
                Пятый,Десять,male,1990-01-01,10 km,10-5,18+ Male,finished,5000.0,4900.0,5.0,5.0,5.0,5.0,5.0,5.0
                Первый,Марафон,male,1990-01-01,42.2 km,42-1,18+ Male,finished,6000.0,7900.0,1.0,1.0,1.0,3.0,3.0,3.0
                Второй,Марафон,male,1990-01-01,42.2 km,42-2,18+ Male,finished,7000.0,6900.0,2.0,2.0,2.0,2.0,2.0,2.0
                Третий,Марафон,male,1990-01-01,42.2 km,42-3,18+ Male,finished,8000.0,5900.0,3.0,3.0,3.0,1.0,1.0,1.0
                """;
    }

    private static String freeTableCsv() {
        return header() + """
                Анна,Первая,female,1990-01-01,Массовый старт,N-1,18+ Female,finished,1000.0,6000.0,1.0,1.0,1.0,6.0,3.0,3.0
                Борис,Второй,male,1990-01-01,Массовый старт,N-2,18+ Male,finished,2000.0,1000.0,2.0,1.0,1.0,1.0,1.0,1.0
                Вера,Третья,female,1990-01-01,Массовый старт,N-3,18+ Female,finished,3000.0,3000.0,3.0,2.0,2.0,3.0,2.0,2.0
                Глеб,Четвёртый,male,1990-01-01,Массовый старт,N-4,18+ Male,finished,4000.0,2000.0,4.0,2.0,2.0,2.0,2.0,2.0
                Дарья,Пятая,female,1990-01-01,Массовый старт,N-5,18+ Female,finished,5000.0,4000.0,5.0,3.0,3.0,4.0,3.0,3.0
                Егор,Шестой,male,1990-01-01,Массовый старт,N-6,18+ Male,finished,6000.0,5000.0,6.0,3.0,3.0,5.0,3.0,3.0
                """;
    }

    private static String heroConfigurationCsv() {
        return header() + """
                Мария,Масс,female,1990-01-01,mass,M-1,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Команда,Тим,,,teams,T-1,Open,finished,2000.0,1900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Чемпион,Чип,male,1990-01-01,champ,C-1,Open,finished,3000.0,2500.0,1.0,1.0,1.0,1.0,1.0,1.0
                Корпоратив,Скрытый,,,corp,P-1,Open,finished,4000.0,3900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String separatedFormatsCsv() {
        return header() + """
                Алиса,Только А,female,1990-01-01,Race A,A-1,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Борис,Только Б,male,1990-01-01,Race B,B-1,Open,finished,2000.0,1900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Команда,Только В,,,Race C,C-1,Open,finished,3000.0,2900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String header() {
        return "name,surname,gender,birthdate,event,dorsal,category,status,times.official_:::finish:::,times.real_:::finish:::,rankings_:::full-1:::,rankings.gen_:::full-1:::,rankings.cat_:::full-1:::,netrankings_:::full-1:::,netrankings.gen_:::full-1:::,netrankings.cat_:::full-1:::\n";
    }
}
