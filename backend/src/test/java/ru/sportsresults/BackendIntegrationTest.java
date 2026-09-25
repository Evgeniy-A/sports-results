package ru.sportsresults;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import ru.sportsresults.api.dto.ImportReportDto;
import ru.sportsresults.api.dto.ImportApplyResponseDto;
import ru.sportsresults.api.dto.ImportPreviewResponseDto;
import ru.sportsresults.api.dto.AwardPolicyDto;
import ru.sportsresults.api.dto.PageResponse;
import ru.sportsresults.api.dto.RaceDto;
import ru.sportsresults.api.dto.RankingAchievementDto;
import ru.sportsresults.api.dto.ResultListItemDto;
import ru.sportsresults.api.dto.ResultIssueJournalExportRequest;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AttachmentScanStatus;
import ru.sportsresults.domain.AttachmentUploadStatus;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.EventPublicationStatus;
import ru.sportsresults.domain.EventSeries;
import ru.sportsresults.domain.CategoryGender;
import ru.sportsresults.domain.PrimaryStandingMode;
import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.domain.ImportBatchStatus;
import ru.sportsresults.domain.ImportBatch;
import ru.sportsresults.domain.ImportScopeType;
import ru.sportsresults.domain.ImportOperation;
import ru.sportsresults.domain.ImportOperationItem;
import ru.sportsresults.domain.ImportOperationMode;
import ru.sportsresults.domain.ImportOperationStatus;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.RaceResultPublicationHistory;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.RegistrationEntryKind;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.ResultIssueAttachment;
import ru.sportsresults.domain.ResultInquiryAvailability;
import ru.sportsresults.domain.ResultInquiryDeadlineMode;
import ru.sportsresults.domain.ResultInquiryLookupState;
import ru.sportsresults.domain.ResultCorrectionReason;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.domain.ResultIssueAttachmentShareGrant;
import ru.sportsresults.domain.ResultIssueArchiveReason;
import ru.sportsresults.domain.ResultIssueHistoryAction;
import ru.sportsresults.domain.ResultIssueQueueScope;
import ru.sportsresults.domain.ResultIssueSnapshotOrigin;
import ru.sportsresults.domain.ResultIssueStatus;
import ru.sportsresults.domain.ResultIssueType;
import ru.sportsresults.domain.ResultsPublicationStatus;
import ru.sportsresults.domain.StartCluster;
import ru.sportsresults.importing.TabularImportFileReader;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.CategoryRepository;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.EventSeriesRepository;
import ru.sportsresults.repository.ImportBatchRepository;
import ru.sportsresults.repository.ImportOperationRepository;
import ru.sportsresults.repository.ImportOperationItemRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RaceResultPublicationHistoryRepository;
import ru.sportsresults.repository.RegistrationRepository;
import ru.sportsresults.repository.ResultRepository;
import ru.sportsresults.repository.ResultIssueRequestRepository;
import ru.sportsresults.repository.ResultIssueAttachmentRepository;
import ru.sportsresults.repository.ResultIssueHistoryRepository;
import ru.sportsresults.repository.ResultIssueAttachmentShareGrantRepository;
import ru.sportsresults.repository.ResultIssueShareBatchRepository;
import ru.sportsresults.repository.StartClusterRepository;
import ru.sportsresults.service.ImportService;
import ru.sportsresults.service.ImportPreviewService;
import ru.sportsresults.service.ImportApplyService;
import ru.sportsresults.service.AwardPolicyService;
import ru.sportsresults.service.EventService;
import ru.sportsresults.api.dto.UpdateAwardPolicyRequest;
import ru.sportsresults.api.dto.UpdateEventRequest;
import ru.sportsresults.api.dto.UpdateRegistrationRequest;
import ru.sportsresults.api.dto.UpdateResultRequest;
import ru.sportsresults.api.dto.UpdateResultInquirySettingsRequest;
import ru.sportsresults.api.dto.UpsertRaceRequest;
import ru.sportsresults.api.dto.UpsertCategoryRequest;
import ru.sportsresults.api.dto.ResultRecalculationApplyDto;
import ru.sportsresults.api.dto.ResultRecalculationPreviewDto;
import ru.sportsresults.service.ResultQueryService;
import ru.sportsresults.service.ResultInquiryService;
import ru.sportsresults.service.ResultIssueAttachmentService;
import ru.sportsresults.service.ResultIssueAttachmentScanService;
import ru.sportsresults.service.EventContentService;
import ru.sportsresults.service.EventDocumentService;
import ru.sportsresults.service.StartClusterService;
import ru.sportsresults.service.RaceAdminService;
import ru.sportsresults.service.RaceResultsPublicationService;
import ru.sportsresults.service.CategoryAdminService;
import ru.sportsresults.service.ResultRecalculationService;
import ru.sportsresults.service.AdminResultService;
import ru.sportsresults.service.AdminResultIssueService;
import ru.sportsresults.service.ResultIssueLifecycleService;
import ru.sportsresults.service.ResultIssueSnapshotService;
import ru.sportsresults.service.ResultIssueJournalExportArtifact;
import ru.sportsresults.service.ResultIssueJournalXlsxExportService;
import ru.sportsresults.service.ResultIssueShareTokenService;
import ru.sportsresults.service.InvalidRequestException;
import ru.sportsresults.service.ResourceNotFoundException;
import ru.sportsresults.service.RequestConflictException;
import ru.sportsresults.storage.FileStorageService;
import ru.sportsresults.storage.attachments.InMemoryAttachmentObjectStorage;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackendIntegrationTest {

    private static final String ADMIN_USERNAME = "integration-admin";
    private static final String ADMIN_PASSWORD = "integration-secret";

    private EmbeddedPostgres postgres;
    private ConfigurableApplicationContext context;
    private MockMvc mockMvc;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private EventRepository eventRepository;
    private EventSeriesRepository eventSeriesRepository;
    private RaceRepository raceRepository;
    private RaceResultPublicationHistoryRepository raceResultPublicationHistoryRepository;
    private CategoryRepository categoryRepository;
    private ImportBatchRepository importBatchRepository;
    private ImportOperationRepository importOperationRepository;
    private ImportOperationItemRepository importOperationItemRepository;
    private RegistrationRepository registrationRepository;
    private ResultRepository resultRepository;
    private ResultIssueRequestRepository resultIssueRequestRepository;
    private ResultIssueAttachmentRepository resultIssueAttachmentRepository;
    private ResultIssueAttachmentShareGrantRepository resultIssueAttachmentShareGrantRepository;
    private ResultIssueShareBatchRepository resultIssueShareBatchRepository;
    private ResultIssueHistoryRepository resultIssueHistoryRepository;
    private StartClusterRepository startClusterRepository;
    private AdminChangeLogRepository changeLogRepository;
    private ImportService importService;
    private ImportPreviewService importPreviewService;
    private ImportApplyService importApplyService;
    private ResultQueryService resultQueryService;
    private ResultInquiryService resultInquiryService;
    private ResultIssueAttachmentService resultIssueAttachmentService;
    private ResultIssueAttachmentScanService resultIssueAttachmentScanService;
    private InMemoryAttachmentObjectStorage attachmentObjectStorage;
    private EventService eventService;
    private AwardPolicyService rawAwardPolicyService;
    private DraftAwareAwardPolicyService awardPolicyService;
    private EventContentService eventContentService;
    private EventDocumentService eventDocumentService;
    private StartClusterService startClusterService;
    private FileStorageService fileStorageService;
    private AdminResultService adminResultService;
    private AdminResultIssueService adminResultIssueService;
    private ResultIssueLifecycleService resultIssueLifecycleService;
    private ResultIssueSnapshotService resultIssueSnapshotService;
    private ResultIssueJournalXlsxExportService resultIssueJournalXlsxExportService;
    private ResultIssueShareTokenService resultIssueShareTokenService;
    private RaceAdminService raceAdminService;
    private RaceResultsPublicationService raceResultsPublicationService;
    private CategoryAdminService categoryAdminService;
    private ResultRecalculationService resultRecalculationService;

    @BeforeAll
    void startApplicationWithPostgres() throws Exception {
        postgres = EmbeddedPostgres.start();
        DataSource dataSource = postgres.getPostgresDatabase();
        String jdbcUrl;
        String username;
        try (Connection connection = dataSource.getConnection()) {
            jdbcUrl = connection.getMetaData().getURL();
            username = connection.getMetaData().getUserName();
        }

        context = new SpringApplicationBuilder(SportsResultsApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + jdbcUrl,
                        "--spring.datasource.username=" + username,
                        "--spring.datasource.password=",
                        "--app.admin.username=" + ADMIN_USERNAME,
                        "--app.admin.password=" + ADMIN_PASSWORD,
                        "--app.documents.storage-root=" + Path.of("target", "test-documents").toAbsolutePath(),
                        "--spring.profiles.active=local",
                        "--app.result-issues.attachments.storage-provider=memory",
                        "--app.result-issues.attachments.scanner-provider=fake",
                        "--app.result-issues.attachments.fake-scanner.result=CLEAN",
                        "--app.public-base-url=https://results.test",
                        "--app.cors.allowed-origins=https://sports-results.pages.dev",
                        "--logging.level.root=WARN"
                );

        mockMvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        jdbcTemplate = context.getBean(JdbcTemplate.class);
        objectMapper = context.getBean(ObjectMapper.class);
        eventRepository = context.getBean(EventRepository.class);
        eventSeriesRepository = context.getBean(EventSeriesRepository.class);
        raceRepository = context.getBean(RaceRepository.class);
        raceResultPublicationHistoryRepository = context.getBean(RaceResultPublicationHistoryRepository.class);
        categoryRepository = context.getBean(CategoryRepository.class);
        importBatchRepository = context.getBean(ImportBatchRepository.class);
        importOperationRepository = context.getBean(ImportOperationRepository.class);
        importOperationItemRepository = context.getBean(ImportOperationItemRepository.class);
        registrationRepository = context.getBean(RegistrationRepository.class);
        resultRepository = context.getBean(ResultRepository.class);
        resultIssueRequestRepository = context.getBean(ResultIssueRequestRepository.class);
        resultIssueAttachmentRepository = context.getBean(ResultIssueAttachmentRepository.class);
        resultIssueAttachmentShareGrantRepository = context.getBean(ResultIssueAttachmentShareGrantRepository.class);
        resultIssueShareBatchRepository = context.getBean(ResultIssueShareBatchRepository.class);
        resultIssueHistoryRepository = context.getBean(ResultIssueHistoryRepository.class);
        startClusterRepository = context.getBean(StartClusterRepository.class);
        changeLogRepository = context.getBean(AdminChangeLogRepository.class);
        importService = context.getBean(ImportService.class);
        importPreviewService = context.getBean(ImportPreviewService.class);
        importApplyService = context.getBean(ImportApplyService.class);
        resultQueryService = context.getBean(ResultQueryService.class);
        resultInquiryService = context.getBean(ResultInquiryService.class);
        resultIssueAttachmentService = context.getBean(ResultIssueAttachmentService.class);
        resultIssueAttachmentScanService = context.getBean(ResultIssueAttachmentScanService.class);
        attachmentObjectStorage = context.getBean(InMemoryAttachmentObjectStorage.class);
        eventService = context.getBean(EventService.class);
        rawAwardPolicyService = context.getBean(AwardPolicyService.class);
        awardPolicyService = new DraftAwareAwardPolicyService(rawAwardPolicyService);
        eventContentService = context.getBean(EventContentService.class);
        eventDocumentService = context.getBean(EventDocumentService.class);
        startClusterService = context.getBean(StartClusterService.class);
        fileStorageService = context.getBean(FileStorageService.class);
        adminResultService = context.getBean(AdminResultService.class);
        adminResultIssueService = context.getBean(AdminResultIssueService.class);
        resultIssueLifecycleService = context.getBean(ResultIssueLifecycleService.class);
        resultIssueSnapshotService = context.getBean(ResultIssueSnapshotService.class);
        resultIssueJournalXlsxExportService = context.getBean(ResultIssueJournalXlsxExportService.class);
        resultIssueShareTokenService = context.getBean(ResultIssueShareTokenService.class);
        raceAdminService = context.getBean(RaceAdminService.class);
        raceResultsPublicationService = context.getBean(RaceResultsPublicationService.class);
        categoryAdminService = context.getBean(CategoryAdminService.class);
        resultRecalculationService = context.getBean(ResultRecalculationService.class);
    }

    @AfterAll
    void stopApplicationAndPostgres() throws Exception {
        if (context != null) {
            context.close();
        }
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    result_recalculation_operation_races, result_recalculation_operations,
                    import_mapping_profiles,
                    import_operation_items, import_operation_races, import_operations,
                    admin_change_logs, race_result_publication_history,
                    result_issue_attachment_share_grants, result_issue_share_batches,
                    result_issue_history, result_issue_attachments, result_issue_requests, splits, results,
                    registrations, import_batches,
                    award_policies, checkpoints, categories, races, events, event_series
                RESTART IDENTITY CASCADE
                """);
        attachmentObjectStorage.clear();
    }

    @Test
    void corsAllowsOnlyTheConfiguredFrontendAndKeepsAdminAuthenticationRequired() throws Exception {
        String allowedOrigin = "https://sports-results.pages.dev";

        mockMvc.perform(options("/api/admin/events")
                        .header("Origin", allowedOrigin)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", allowedOrigin))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string(
                        "Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsString("Authorization")
                ));

        mockMvc.perform(get("/api/admin/events").header("Origin", allowedOrigin))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", allowedOrigin));

        mockMvc.perform(options("/api/admin/events")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void eventAndSeriesCreationGenerateUniqueSlugsWhileRenameKeepsExistingUrls() throws Exception {
        MvcResult firstSeriesResponse = mockMvc.perform(post("/api/admin/event-series")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Гонка Героев","description":null,"active":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("gonka-geroev"))
                .andReturn();
        long firstSeriesId = objectMapper.readTree(firstSeriesResponse.getResponse().getContentAsByteArray())
                .get("id").asLong();

        mockMvc.perform(post("/api/admin/event-series")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Гонка Героев","description":null,"active":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("gonka-geroev-2"));

        String eventBody = """
                {"eventSeriesId":%d,"name":"Гонка Героев — Казань 2027",
                 "startsAt":null,"endsAt":null,"location":"Казань",
                 "timeZone":"Europe/Moscow","publicationStatus":"DRAFT"}
                """.formatted(firstSeriesId);
        MvcResult firstEventResponse = mockMvc.perform(post("/api/admin/events")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("gonka-geroev-kazan-2027"))
                .andReturn();
        long eventId = objectMapper.readTree(firstEventResponse.getResponse().getContentAsByteArray())
                .get("id").asLong();

        mockMvc.perform(post("/api/admin/events")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("gonka-geroev-kazan-2027-2"));

        mockMvc.perform(put("/api/admin/event-series/{seriesId}", firstSeriesId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Гонка Героев — новая редакция","description":null,"active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Гонка Героев — новая редакция"))
                .andExpect(jsonPath("$.slug").value("gonka-geroev"));

        mockMvc.perform(put("/api/admin/events/{eventId}", eventId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventSeriesId":%d,"name":"Гонка Героев — Казань 2027. Весна",
                                 "startsAt":null,"endsAt":null,"location":"Казань",
                                 "timeZone":"Europe/Moscow","publicationStatus":"DRAFT"}
                                """.formatted(firstSeriesId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Гонка Героев — Казань 2027. Весна"))
                .andExpect(jsonPath("$.slug").value("gonka-geroev-kazan-2027"));
    }

    @Test
    void adminEventReadsIncludeDraftEventsHiddenRacesAndRecalculationState() throws Exception {
        EventSeries series = createSeries("Admin Series", "admin-series");
        Event event = createEvent(
                series,
                "Hidden admin event",
                "hidden-admin-event",
                "Perm",
                "2028-04-12T06:00:00Z"
        );
        event.setPublicationStatus(EventPublicationStatus.DRAFT);
        event.setResultsPublicationStatus(ResultsPublicationStatus.DRAFT);
        eventRepository.saveAndFlush(event);

        RaceDto raceDto = raceAdminService.create(event.getId(), new UpsertRaceRequest(
                "admin-race", "Admin race", "admin-race", null, null,
                0, false
        ), ADMIN_USERNAME);
        Race race = raceRepository.findById(raceDto.id()).orElseThrow();
        race.setResultRecalculationRequired(true);
        raceRepository.saveAndFlush(race);

        mockMvc.perform(get("/api/admin/events"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/events")
                        .param("name", "hidden")
                        .param("publicationStatus", "DRAFT")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(event.getId()))
                .andExpect(jsonPath("$.content[0].publicationStatus").value("DRAFT"));

        mockMvc.perform(get("/api/admin/events/{eventId}", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Hidden admin event"));

        mockMvc.perform(get("/api/admin/events/{eventId}/races", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(race.getId()))
                .andExpect(jsonPath("$[0].publicVisible").value(false))
                .andExpect(jsonPath("$[0].resultRecalculationRequired").value(true));
    }

    @Test
    void raceAdminCreateAndRenameKeepIdentityAndStableSlug() throws Exception {
        Event event = createPublishedEvent("Race-only model", "race-only-model");

        MvcResult createdResponse = mockMvc.perform(post("/api/admin/events/{eventId}/races", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Детский забег",
                                  "distanceMeters":null,
                                  "startsAt":null,
                                  "displayOrder":0,
                                  "publicVisible":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Детский забег"))
                .andExpect(jsonPath("$.publicVisible").value(true))
                .andExpect(jsonPath("$.entryMode").doesNotExist())
                .andExpect(jsonPath("$.sportFormatId").doesNotExist())
                .andReturn();
        RaceDto created = objectMapper.readValue(createdResponse.getResponse().getContentAsByteArray(), RaceDto.class);
        Race persisted = raceRepository.findById(created.id()).orElseThrow();
        Long originalId = persisted.getId();
        String originalSlug = persisted.getSlug();
        String originalSourceCode = persisted.getSourceCode();
        assertThat(originalSourceCode).isEqualTo("internal-detskiy-zabeg");

        mockMvc.perform(put("/api/admin/events/{eventId}/races/{raceId}", event.getId(), originalId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Детский забег 2 км",
                                  "distanceMeters":2000,
                                  "startsAt":null,
                                  "displayOrder":0,
                                  "publicVisible":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(originalId))
                .andExpect(jsonPath("$.slug").value(originalSlug))
                .andExpect(jsonPath("$.sourceCode").value(originalSourceCode))
                .andExpect(jsonPath("$.name").value("Детский забег 2 км"));

        mockMvc.perform(post("/api/admin/events/{eventId}/races", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                   "sourceCode":"mass-10",
                                   "name":"Масс-старт 10 км",
                                   "slug":"mass-10-km",
                                   "displayOrder":0,
                                   "publicVisible":true
                                 }
                                 """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Масс-старт 10 км"));

        mockMvc.perform(get("/api/events/slug/{slug}", event.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sportFormats").doesNotExist())
                .andExpect(jsonPath("$.races.length()").value(2))
                .andExpect(jsonPath("$.races[0].id").value(originalId))
                .andExpect(jsonPath("$.races[0].name").value("Детский забег 2 км"))
                .andExpect(jsonPath("$.races[1].name").value("Масс-старт 10 км"));
    }

    @Test
    void adminResultSearchScopesByRaceAndExposesParticipantFacts() {
        Event event = createPublishedEvent("Admin race filter", "admin-race-filter");
        importPublishedFixture(event.getId(), "format-filter.csv", rankingCsv().getBytes(StandardCharsets.UTF_8));
        List<Race> importedRaces = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId());
        Race firstRace = importedRaces.stream().filter(race -> race.getSourceCode().equals("5 km")).findFirst().orElseThrow();
        Race secondRace = importedRaces.stream().filter(race -> race.getSourceCode().equals("10 km")).findFirst().orElseThrow();
        var firstPage = resultQueryService.searchAdmin(
                event.getId(), firstRace.getId(), null, null, null, null, null, null,
                0, 50, "bib", "asc", RankingBasis.GUN_TIME
        );
        var secondPage = resultQueryService.searchAdmin(
                event.getId(), secondRace.getId(), null, null, null, null, null, null,
                0, 50, "bib", "asc", RankingBasis.GUN_TIME
        );

        assertThat(firstPage.content()).isNotEmpty().allSatisfy(row -> {
            assertThat(row.raceId()).isEqualTo(firstRace.getId());
            assertThat(row.entryKind()).isNotNull();
        });
        assertThat(secondPage.content()).isNotEmpty()
                .allSatisfy(row -> assertThat(row.raceId()).isEqualTo(secondRace.getId()));
    }

    @Test
    void flywayCreatesAndHibernateValidatesAllTables() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'events', 'races', 'categories', 'registrations', 'results',
                    'checkpoints', 'splits', 'import_batches', 'admin_change_logs',
                    'event_series', 'award_policies', 'event_participant_info',
                    'event_info_blocks', 'event_schedule_items', 'event_documents', 'start_clusters',
                    'result_issue_requests', 'result_issue_attachments', 'import_operations', 'import_operation_races',
                    'import_operation_items', 'race_result_publication_history', 'result_issue_history',
                    'result_recalculation_operations', 'result_recalculation_operation_races',
                    'result_issue_share_batches', 'result_issue_attachment_share_grants'
                  )
                """, Integer.class);
        assertThat(count).isEqualTo(27);
        Integer rankingColumn = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'races'
                  AND column_name = 'public_ranking_basis'
                  AND is_nullable = 'NO'
                  AND column_default LIKE '%CHIP_TIME%'
                """, Integer.class);
        assertThat(rankingColumn).isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='races'
                  AND column_name='results_publication_status'
                  AND is_nullable='NO' AND column_default LIKE '%DRAFT%'
                """, Integer.class)).isOne();
        Integer ageCategoryColumns = jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND (
                    (table_name = 'award_policies' AND column_name = 'age_calculation_mode')
                    OR (table_name = 'registrations' AND column_name = 'source_category')
                    OR (table_name = 'categories' AND column_name IN ('min_age', 'max_age', 'gender', 'enabled'))
                  )
                """, Integer.class);
        assertThat(ageCategoryColumns).isEqualTo(6);
        String auditConstraint = jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname = 'ck_admin_change_logs_entity_type'
                """, String.class);
        assertThat(auditConstraint).contains("SPORT_FORMAT", "RACE", "CATEGORY");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='events'
                  AND column_name='result_data_revision' AND data_type='bigint' AND is_nullable='NO'
                """, Integer.class)).isOne();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='races'
                  AND column_name='result_recalculation_required'
                  AND is_nullable='NO' AND column_default='false'
                """, Integer.class)).isOne();
    }

    @Test
    void previewsSyntheticImportsReadOnlyWithExplicitScopeRevisionDigestAndDuplicateDiagnostics() throws Exception {
        byte[] m52Csv = Files.readAllBytes(sample("results_m52_2025.csv"));
        Event m52 = createPublishedEvent("M52 Stage A", "m52-stage-a");
        assertThat(importPublishedFixture(m52.getId(), "results_m52_2025.csv", m52Csv).status())
                .isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race tenKm = raceRepository.findByEventIdAndSourceCode(m52.getId(), "10 km").orElseThrow();
        Race marathon = raceRepository.findByEventIdAndSourceCode(m52.getId(), "42.2 km").orElseThrow();
        m52 = eventRepository.findById(m52.getId()).orElseThrow();
        m52.setResultDataRevision(9);
        eventRepository.saveAndFlush(m52);

        long m52RegistrationsBefore = registrationRepository.countByRaceEventId(m52.getId());
        long m52ResultsBefore = resultRepository.countByRegistrationRaceEventId(m52.getId());
        long batchesBefore = importBatchRepository.count();
        Map<String, Object> registrationBefore = jdbcTemplate.queryForMap("""
                SELECT id, race_id, bib, display_name, birth_date, source_category, cluster_id
                FROM registrations WHERE race_id IN (?, ?) ORDER BY id LIMIT 1
                """, tenKm.getId(), marathon.getId());
        Map<String, Object> resultBefore = jdbcTemplate.queryForMap("""
                SELECT id, status, gun_time_ms, chip_time_ms, overall_place
                FROM results WHERE registration_id=?
                """, registrationBefore.get("id"));

        MockMultipartFile m52File = new MockMultipartFile(
                "file", "results_m52_2025.csv", "text/csv", m52Csv
        );
        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/preview", m52.getId())
                        .file(m52File)
                        .param("mode", "ADD_NEW")
                        .param("raceIds", tenKm.getId().toString())
                        .param("rowLimit", "3"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/preview", m52.getId())
                        .file(m52File)
                        .param("mode", "ADD_NEW")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isBadRequest());

        String addPreview = mockMvc.perform(multipart(
                                "/api/admin/events/{eventId}/imports/preview", m52.getId())
                        .file(m52File)
                        .param("mode", "ADD_NEW")
                        .param("raceIds", tenKm.getId().toString())
                        .param("rowLimit", "3")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationStatus").value("PREVIEWED"))
                .andExpect(jsonPath("$.mode").value("ADD_NEW"))
                .andExpect(jsonPath("$.baseRevision").value(9))
                .andExpect(jsonPath("$.totals.totalRows").value(31))
                .andExpect(jsonPath("$.totals.inScopeRows").value(23))
                .andExpect(jsonPath("$.totals.unchangedCount").value(23))
                .andExpect(jsonPath("$.totals.outOfScopeCount").value(8))
                .andExpect(jsonPath("$.modeSummary.existingSkipped").value(23))
                .andExpect(jsonPath("$.blockingErrorsPresent").value(false))
                .andExpect(jsonPath("$.rowLimit").value(3))
                .andExpect(jsonPath("$.rowsTruncated").value(true))
                .andExpect(jsonPath("$.rows.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        String addDigest = objectMapper.readTree(addPreview).get("planDigest").asText();
        String m52FileSha = objectMapper.readTree(addPreview).get("fileSha256").asText();

        MockMultipartFile repeatedM52File = new MockMultipartFile(
                "file", "renamed-m52.csv", "text/csv", m52Csv
        );
        String updatePreview = mockMvc.perform(multipart(
                                "/api/admin/events/{eventId}/imports/preview", m52.getId())
                        .file(repeatedM52File)
                        .param("mode", "UPDATE_EXISTING")
                        .param("raceIds", marathon.getId().toString())
                        .param("rowLimit", "0")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseRevision").value(9))
                .andExpect(jsonPath("$.totals.inScopeRows").value(8))
                .andExpect(jsonPath("$.totals.unchangedCount").value(8))
                .andExpect(jsonPath("$.totals.outOfScopeCount").value(23))
                .andExpect(jsonPath("$.modeSummary.unchanged").value(8))
                .andExpect(jsonPath("$.rows").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(updatePreview).get("planDigest").asText()).isNotEqualTo(addDigest);

        StringBuilder repeatedBibCsv = new StringBuilder(csvHeader());
        for (int index = 0; index < 21; index++) {
            repeatedBibCsv.append("Duplicate,Runner,male,1990-01-01,10 km,DUP-1,Open,finished,")
                    .append(1_000 + index).append(".0,900.0,1.0,1.0,1.0,1.0,1.0,1.0\n");
        }
        MockMultipartFile repeatedBibFile = new MockMultipartFile(
                "file", "repeated-bib.csv", "text/csv",
                repeatedBibCsv.toString().getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/preview", m52.getId())
                        .file(repeatedBibFile)
                        .param("mode", "ADD_NEW")
                        .param("raceIds", tenKm.getId().toString())
                        .param("rowLimit", "0")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.duplicateCount").value(21))
                .andExpect(jsonPath("$.duplicateGroupsTruncated").value(false))
                .andExpect(jsonPath("$.duplicateBibs[0].rowsTruncated").value(true))
                .andExpect(jsonPath("$.duplicateBibs[0].rows.length()").value(20));

        byte[] heroCsv = Files.readAllBytes(sample("results_gonka2026.csv"));
        Event hero = createPublishedEvent("Hero Stage A", "hero-stage-a");
        assertThat(importPublishedFixture(hero.getId(), "results_gonka2026.csv", heroCsv).status())
                .isEqualTo(ImportBatchStatus.SUCCEEDED);
        long heroRegistrationsBefore = registrationRepository.countByRaceEventId(hero.getId());
        long heroResultsBefore = resultRepository.countByRegistrationRaceEventId(hero.getId());
        String[] heroRaceIds = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(hero.getId()).stream()
                .map(race -> race.getId().toString())
                .toArray(String[]::new);
        MockMultipartFile heroFile = new MockMultipartFile(
                "file", "results_gonka2026.csv", "text/csv", heroCsv
        );
        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/preview", hero.getId())
                        .file(heroFile)
                        .param("mode", "UPDATE_EXISTING")
                        .param("raceIds", heroRaceIds)
                        .param("rowLimit", "0")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingErrorsPresent").value(true))
                .andExpect(jsonPath("$.totals.totalRows").value(16))
                .andExpect(jsonPath("$.totals.duplicateCount").value(2))
                .andExpect(jsonPath("$.duplicateBibs[0].bib").value("SYN-DUP"))
                .andExpect(jsonPath("$.duplicateBibs[0].rows[0].sourceRowNumber").value(4))
                .andExpect(jsonPath("$.duplicateBibs[0].rows[1].sourceRowNumber").value(7));

        assertThat(registrationRepository.countByRaceEventId(m52.getId())).isEqualTo(m52RegistrationsBefore);
        assertThat(resultRepository.countByRegistrationRaceEventId(m52.getId())).isEqualTo(m52ResultsBefore);
        assertThat(registrationRepository.countByRaceEventId(hero.getId())).isEqualTo(heroRegistrationsBefore);
        assertThat(resultRepository.countByRegistrationRaceEventId(hero.getId())).isEqualTo(heroResultsBefore);
        assertThat(importBatchRepository.count()).isEqualTo(batchesBefore + 1);
        assertThat(eventRepository.findById(m52.getId()).orElseThrow().getResultDataRevision()).isEqualTo(9);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT id, race_id, bib, display_name, birth_date, source_category, cluster_id
                FROM registrations WHERE id=?
                """, registrationBefore.get("id"))).isEqualTo(registrationBefore);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT id, status, gun_time_ms, chip_time_ms, overall_place
                FROM results WHERE id=?
                """, resultBefore.get("id"))).isEqualTo(resultBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM import_operations", Long.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM import_operations
                WHERE event_id=? AND file_sha256=?
                  AND created_by=? AND base_revision=9
                """, Long.class, m52.getId(), m52FileSha, ADMIN_USERNAME)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM import_operation_races operation_race
                JOIN import_operations operation ON operation.id=operation_race.operation_id
                WHERE operation.event_id=?
                """, Long.class, m52.getId())).isEqualTo(3);
    }

    @Test
    void appliesAddNewOnlyPreservesExistingFactsAndReturnsIdempotentResult() throws Exception {
        Event event = createPublishedEvent("Stage B ADD_NEW", "stage-b-add-new");
        assertThat(importPublishedFixture(
                event.getId(), "initial.csv", oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        var cluster = startClusterService.create(event.getId(), race.getId(),
                new ru.sportsresults.api.dto.UpsertStartClusterRequest(
                        "A", null, "Cluster A", 0, null), ADMIN_USERNAME);
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 3, false,
                AgeCalculationMode.EVENT_DATE, 0, false), ADMIN_USERNAME);

        Registration existing = registrationRepository.findAllByRaceIdAndBib(race.getId(), "A-1").getFirst();
        Result existingResult = resultRepository.findByRegistrationId(existing.getId()).orElseThrow();
        Map<String, Object> existingRegistrationBefore = jdbcTemplate.queryForMap("""
                SELECT id, race_id, category_id, cluster_id, import_batch_id, bib, display_name,
                       first_name, last_name, birth_date, gender, source_category,
                       source_row_number, source_row_hash, updated_at
                FROM registrations WHERE id=?
                """, existing.getId());
        Map<String, Object> existingResultBefore = jdbcTemplate.queryForMap("""
                SELECT id, registration_id, status, gun_time_ms, chip_time_ms,
                       overall_place, gender_place, category_place,
                       net_overall_place, net_gender_place, net_category_place, updated_at
                FROM results WHERE id=?
                """, existingResult.getId());
        long baseRevision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        byte[] csv = stageBAddCsv().getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "add.csv", csv, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(preview.baseRevision()).isEqualTo(baseRevision);
        assertThat(preview.totals().newCount()).isEqualTo(2);
        assertThat(preview.totals().changedCount()).isOne();
        assertThat(preview.blockingErrorsPresent()).isFalse();

        MockMultipartFile file = new MockMultipartFile("file", "renamed.csv", "text/csv", csv);
        mockMvc.perform(multipart(
                        "/api/admin/events/{eventId}/imports/{operationId}/apply",
                        event.getId(), preview.operationId())
                        .file(file))
                .andExpect(status().isUnauthorized());
        String response = mockMvc.perform(multipart(
                        "/api/admin/events/{eventId}/imports/{operationId}/apply",
                        event.getId(), preview.operationId())
                        .file(file)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.mode").value("ADD_NEW"))
                .andExpect(jsonPath("$.insertedCount").value(2))
                .andExpect(jsonPath("$.existingSkippedCount").value(1))
                .andExpect(jsonPath("$.outOfScopeCount").value(0))
                .andExpect(jsonPath("$.newRevision").value(baseRevision + 1))
                .andReturn().getResponse().getContentAsString();
        Long batchId = objectMapper.readTree(response).get("importBatchId").asLong();

        assertThat(registrationRepository.countByRaceEventId(event.getId())).isEqualTo(3);
        assertThat(resultRepository.countByRegistrationRaceEventId(event.getId())).isEqualTo(3);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT id, race_id, category_id, cluster_id, import_batch_id, bib, display_name,
                       first_name, last_name, birth_date, gender, source_category,
                       source_row_number, source_row_hash, updated_at
                FROM registrations WHERE id=?
                """, existing.getId())).isEqualTo(existingRegistrationBefore);
        assertThat(jdbcTemplate.queryForMap("""
                SELECT id, registration_id, status, gun_time_ms, chip_time_ms,
                       overall_place, gender_place, category_place,
                       net_overall_place, net_gender_place, net_category_place, updated_at
                FROM results WHERE id=?
                """, existingResult.getId())).isEqualTo(existingResultBefore);

        Registration clustered = registrationRepository.findAllByRaceIdAndBib(race.getId(), "N-1").getFirst();
        Registration withoutCluster = registrationRepository.findAllByRaceIdAndBib(race.getId(), "N-2").getFirst();
        clustered = registrationRepository.findById(clustered.getId()).orElseThrow();
        withoutCluster = registrationRepository.findById(withoutCluster.getId()).orElseThrow();
        assertThat(clustered.getCluster().getId()).isEqualTo(cluster.id());
        assertThat(clustered.getSourceCategory()).isEqualTo("New Class");
        assertThat(clustered.getCategory()).isNotNull();
        assertThat(clustered.getCategory().getSourceName()).isEqualTo("New Class");
        assertThat(withoutCluster.getCluster()).isNull();
        assertThat(resultRepository.findByRegistrationId(withoutCluster.getId()).orElseThrow().getStatus())
                .isEqualTo("notstarted");
        assertThat(resultRepository.findByRegistrationId(withoutCluster.getId()).orElseThrow().getGunTime()).isNull();
        assertThat(importBatchRepository.findById(batchId).orElseThrow()).satisfies(batch -> {
            assertThat(batch.getScopeType()).isEqualTo(ru.sportsresults.domain.ImportScopeType.RACE);
            assertThat(batch.getRace().getId()).isEqualTo(race.getId());
            assertThat(batch.getImportedRows()).isEqualTo(2);
            assertThat(batch.getSkippedRows()).isOne();
        });
        assertThat(importOperationItemRepository.findAllByOperationIdOrderBySourceRowNumberAsc(preview.operationId()))
                .hasSize(3)
                .extracting(item -> item.getDecision().name())
                .containsExactly("EXISTING_CHANGED", "NEW", "NEW");
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ru.sportsresults.domain.ResultsPublicationStatus.PUBLISHED);
        assertThat(resultQueryService.search(
                event.getId(), race.getId(), null, "N-1", null, null, null,
                0, 10, "place", "asc"
        ).content()).singleElement().satisfies(item ->
                assertThat(item.rankingAchievements().getFirst().place()).isEqualTo(2));

        ImportApplyResponseDto retry = importApplyService.apply(
                event.getId(), preview.operationId(), csv, ADMIN_USERNAME
        );
        assertThat(retry.importBatchId()).isEqualTo(batchId);
        assertThat(retry.newRevision()).isEqualTo(baseRevision + 1);
        assertThat(registrationRepository.countByRaceEventId(event.getId())).isEqualTo(3);
        assertThat(importBatchRepository.count()).isEqualTo(2);

        ImportPreviewResponseDto sameShaNewOperation = importPreviewService.preview(
                event.getId(), "same-again.csv", csv, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 0, ADMIN_USERNAME
        );
        assertThat(sameShaNewOperation.fileSha256()).isEqualTo(preview.fileSha256());
        assertThat(sameShaNewOperation.operationId()).isNotEqualTo(preview.operationId());
        assertThat(sameShaNewOperation.totals().newCount()).isZero();
        long batchCountBeforeNoOp = importBatchRepository.count();
        long registrationCountBeforeNoOp = registrationRepository.countByRaceEventId(event.getId());
        long resultCountBeforeNoOp = resultRepository.countByRegistrationRaceEventId(event.getId());
        MockMultipartFile noOpFile = new MockMultipartFile(
                "file", "same-again.csv", "text/csv", csv
        );
        mockMvc.perform(multipart(
                        "/api/admin/events/{eventId}/imports/{operationId}/apply",
                        event.getId(), sameShaNewOperation.operationId())
                        .file(noOpFile)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREVIEWED"))
                .andExpect(jsonPath("$.mode").value("ADD_NEW"))
                .andExpect(jsonPath("$.importBatchId").isEmpty())
                .andExpect(jsonPath("$.insertedCount").value(0))
                .andExpect(jsonPath("$.updatedCount").value(0))
                .andExpect(jsonPath("$.existingSkippedCount").value(3))
                .andExpect(jsonPath("$.unchangedCount").value(sameShaNewOperation.totals().unchangedCount()))
                .andExpect(jsonPath("$.newRevision").value(baseRevision + 1))
                .andExpect(jsonPath("$.appliedAt").isEmpty())
                .andExpect(jsonPath("$.noOp").value(true));
        assertThat(importBatchRepository.count()).isEqualTo(batchCountBeforeNoOp);
        assertThat(registrationRepository.countByRaceEventId(event.getId()))
                .isEqualTo(registrationCountBeforeNoOp);
        assertThat(resultRepository.countByRegistrationRaceEventId(event.getId()))
                .isEqualTo(resultCountBeforeNoOp);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(baseRevision + 1);
        assertThat(importOperationRepository.findById(sameShaNewOperation.operationId()).orElseThrow().getStatus())
                .isEqualTo(ImportOperationStatus.PREVIEWED);
    }

    @Test
    void createsUnknownRaceScopedClustersOnlyOnApplyAndDoesNotDuplicateThem() {
        Event event = createPublishedEvent("Stage J.1 clusters", "stage-j1-clusters");
        assertThat(importPublishedFixture(
                event.getId(), "cluster-base.csv", oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 0, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);
        assertThat(startClusterRepository.countByRaceId(race.getId())).isZero();

        byte[] csv = (csvHeader().stripTrailing() + ",clusterCode\n" + """
                Волна,Альфа,male,1990-01-01,5 km,CL-A,Open,finished,2000.0,1900.0,2.0,2.0,2.0,2.0,2.0,2.0,%s
                Волна,Бета,female,1991-01-01,5 km,CL-B,Open,finished,3000.0,2900.0,3.0,1.0,1.0,3.0,1.0,1.0,B
                """.formatted(" A ")).getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "unknown-clusters.csv", csv, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(preview.blockingErrorsPresent()).isFalse();
        assertThat(preview.totals().newCount()).isEqualTo(2);
        assertThat(preview.rows()).allSatisfy(row -> assertThat(row.diffs())
                .extracting(ImportPreviewResponseDto.FieldDiff::field)
                .contains("clusterDefinition"));
        assertThat(startClusterRepository.countByRaceId(race.getId())).isZero();

        importApplyService.apply(event.getId(), preview.operationId(), csv, ADMIN_USERNAME);
        assertThat(startClusterRepository.findAllByRaceIdOrderByDisplayOrderAscIdAsc(race.getId()))
                .extracting(StartCluster::getDisplayName).containsExactly("A", "B");
        assertThat(registrationRepository.findAllByRaceIdAndBib(race.getId(), "CL-A").getFirst().getCluster())
                .extracting(StartCluster::getDisplayName).isEqualTo("A");

        ImportPreviewResponseDto repeated = importPreviewService.preview(
                event.getId(), "unknown-clusters-again.csv", csv, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(repeated.totals().newCount()).isZero();
        assertThat(repeated.totals().unchangedCount()).isEqualTo(2);
        assertThat(startClusterRepository.countByRaceId(race.getId())).isEqualTo(2);
        assertThat(resultQueryService.search(
                event.getId(), race.getId(), null, "CL-A", null, null, null,
                0, 20, "place", "asc"
        ).content()).singleElement().satisfies(row ->
                assertThat(row.rankingAchievements().getFirst().place()).isEqualTo(2));
    }

    @Test
    void rejectsBlockedAndMismatchedApplyWithoutSportsWrites() {
        Event event = createPublishedEvent("Stage B blockers", "stage-b-blockers");
        byte[] initial = oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8);
        importPublishedFixture(event.getId(), "initial.csv", initial);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        long registrationsBefore = registrationRepository.count();
        long resultsBefore = resultRepository.count();
        long revisionBefore = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();

        byte[] duplicate = duplicateStageBAddCsv().getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto blocked = importPreviewService.preview(
                event.getId(), "duplicate.csv", duplicate, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(blocked.blockingErrorsPresent()).isTrue();
        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), blocked.operationId(), duplicate, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_BLOCKED");

        byte[] newA = singleNewCsv("NEW-A", "1200.0").getBytes(StandardCharsets.UTF_8);
        byte[] newB = singleNewCsv("NEW-B", "1300.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto mismatch = importPreviewService.preview(
                event.getId(), "a.csv", newA, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), mismatch.operationId(), newB, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("FILE_MISMATCH");

        assertThat(registrationRepository.count()).isEqualTo(registrationsBefore);
        assertThat(resultRepository.count()).isEqualTo(resultsBefore);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revisionBefore);
        assertThat(importBatchRepository.count()).isOne();
    }

    @Test
    void appliesOnlyNewRowsInsideSavedRaceScope() {
        Event event = createPublishedEvent("Stage B scope", "stage-b-scope");
        importPublishedFixture(
                event.getId(), "initial.csv", rankingCsv().getBytes(StandardCharsets.UTF_8));
        Race fiveKm = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        Race tenKm = raceRepository.findByEventIdAndSourceCode(event.getId(), "10 km").orElseThrow();
        byte[] csv = scopedStageBAddCsv().getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "scoped.csv", csv, ImportOperationMode.ADD_NEW,
                List.of(fiveKm.getId()), 100, ADMIN_USERNAME
        );
        assertThat(preview.totals().newCount()).isOne();
        assertThat(preview.totals().unchangedCount()).isOne();
        assertThat(preview.totals().outOfScopeCount()).isOne();

        ImportApplyResponseDto applied = importApplyService.apply(
                event.getId(), preview.operationId(), csv, ADMIN_USERNAME
        );
        assertThat(applied.insertedCount()).isOne();
        assertThat(applied.existingSkippedCount()).isOne();
        assertThat(applied.outOfScopeCount()).isOne();
        assertThat(registrationRepository.findAllByRaceIdAndBib(fiveKm.getId(), "SCOPE-IN")).hasSize(1);
        assertThat(registrationRepository.findAllByRaceIdAndBib(tenKm.getId(), "SCOPE-OUT")).isEmpty();
        assertThat(importOperationItemRepository.findAllByOperationIdOrderBySourceRowNumberAsc(preview.operationId()))
                .extracting(item -> item.getDecision().name())
                .containsExactly("EXISTING_UNCHANGED", "NEW", "OUT_OF_SCOPE");
    }

    @Test
    void staleAndDigestChecksProtectAgainstManualWritesAndPlanTampering() {
        Event event = createPublishedEvent("Stage B stale", "stage-b-stale");
        byte[] initial = oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8);
        importPublishedFixture(event.getId(), "initial.csv", initial);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        byte[] add = singleNewCsv("STALE-NEW", "1200.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto stale = importPreviewService.preview(
                event.getId(), "stale.csv", add, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        Result current = resultRepository.findAll().getFirst();
        adminResultService.updateResult(current.getId(), new ru.sportsresults.api.dto.UpdateResultRequest(
                current.getStatus(), 1_100L, current.getChipTime().toMillis(),
                current.getOverallPlace(), current.getGenderPlace(), current.getCategoryPlace(),
                current.getNetOverallPlace(), current.getNetGenderPlace(), current.getNetCategoryPlace()
        ), ADMIN_USERNAME);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(stale.baseRevision() + 1);
        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), stale.operationId(), add, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_STALE");
        assertThat(registrationRepository.findAllByRaceIdAndBib(race.getId(), "STALE-NEW")).isEmpty();

        long beforeRegistrationRevision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        Registration registration = registrationRepository.findAllByRaceIdAndBib(race.getId(), "A-1").getFirst();
        adminResultService.updateRegistration(registration.getId(),
                new ru.sportsresults.api.dto.UpdateRegistrationRequest(
                        registration.getDisplayName() + " corrected", registration.getFirstName(),
                        registration.getLastName(), registration.getBirthDate(), registration.getGender(),
                        registration.getBib(), registration.getSourceCategory(), null, registration.getEntryKind(),
                        registration.getCategory() == null ? null : registration.getCategory().getId()
                ), ADMIN_USERNAME);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(beforeRegistrationRevision + 1);

        ImportPreviewResponseDto digest = importPreviewService.preview(
                event.getId(), "digest.csv", add, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        jdbcTemplate.update("UPDATE import_operations SET plan_digest=repeat('f', 64) WHERE id=?",
                digest.operationId());
        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), digest.operationId(), add, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_STALE");
        assertThat(registrationRepository.findAllByRaceIdAndBib(race.getId(), "STALE-NEW")).isEmpty();
    }

    @Test
    void serializesSameAndDifferentOperationsAtEventLevel() throws Exception {
        Event event = createPublishedEvent("Stage B concurrency", "stage-b-concurrency");
        importPublishedFixture(
                event.getId(), "initial.csv", oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        byte[] sameFile = singleNewCsv("CONCURRENT", "1200.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto sameOperation = importPreviewService.preview(
                event.getId(), "same.csv", sameFile, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ImportApplyResponseDto> sameApply = () -> {
            start.await(10, TimeUnit.SECONDS);
            return importApplyService.apply(event.getId(), sameOperation.operationId(), sameFile, ADMIN_USERNAME);
        };
        var first = pool.submit(sameApply);
        var second = pool.submit(sameApply);
        start.countDown();
        ImportApplyResponseDto firstResponse = first.get(30, TimeUnit.SECONDS);
        ImportApplyResponseDto secondResponse = second.get(30, TimeUnit.SECONDS);
        assertThat(firstResponse.importBatchId()).isEqualTo(secondResponse.importBatchId());
        assertThat(firstResponse.newRevision()).isEqualTo(secondResponse.newRevision());
        assertThat(registrationRepository.findAllByRaceIdAndBib(race.getId(), "CONCURRENT")).hasSize(1);

        byte[] fileA = singleNewCsv("OP-A", "1300.0").getBytes(StandardCharsets.UTF_8);
        byte[] fileB = singleNewCsv("OP-B", "1400.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto operationA = importPreviewService.preview(
                event.getId(), "op-a.csv", fileA, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        ImportPreviewResponseDto operationB = importPreviewService.preview(
                event.getId(), "op-b.csv", fileB, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(operationA.baseRevision()).isEqualTo(operationB.baseRevision());
        importApplyService.apply(event.getId(), operationA.operationId(), fileA, ADMIN_USERNAME);
        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), operationB.operationId(), fileB, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_STALE");
        assertThat(registrationRepository.findAllByRaceIdAndBib(race.getId(), "OP-A")).hasSize(1);
        assertThat(registrationRepository.findAllByRaceIdAndBib(race.getId(), "OP-B")).isEmpty();
        pool.shutdownNow();
    }

    @Test
    void rollsBackSportsRevisionOperationAndNewAuditOnLateDatabaseFailure() {
        Event event = createPublishedEvent("Stage B rollback", "stage-b-rollback");
        importPublishedFixture(
                event.getId(), "initial.csv", oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        byte[] add = singleNewCsv("ROLLBACK", "1200.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "rollback.csv", add, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        jdbcTemplate.update("""
                INSERT INTO import_operation_items(
                    operation_id, source_row_number, bib, decision, action, target_race_id,
                    created_at, updated_at
                ) VALUES (?, 2, 'ROLLBACK', 'NEW', 'INSERT', ?, now(), now())
                """, preview.operationId(), race.getId());
        long registrationsBefore = registrationRepository.count();
        long resultsBefore = resultRepository.count();
        long batchesBefore = importBatchRepository.count();
        long revisionBefore = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        long auditBefore = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM import_operation_items WHERE operation_id=?", Long.class,
                preview.operationId());

        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), preview.operationId(), add, ADMIN_USERNAME
        )).isInstanceOf(RuntimeException.class);

        assertThat(registrationRepository.count()).isEqualTo(registrationsBefore);
        assertThat(resultRepository.count()).isEqualTo(resultsBefore);
        assertThat(importBatchRepository.count()).isEqualTo(batchesBefore);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revisionBefore);
        assertThat(importOperationRepository.findById(preview.operationId()).orElseThrow().getStatus())
                .isEqualTo(ImportOperationStatus.PREVIEWED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM import_operation_items WHERE operation_id=?", Long.class,
                preview.operationId())).isEqualTo(auditBefore);
        assertThat(registrationRepository.findAllByRaceIdAndBib(race.getId(), "ROLLBACK")).isEmpty();
    }

    @Test
    void appliesOnlyMissingSyntheticM52RowsAndBlocksSyntheticHeroDuplicateBib() throws Exception {
        byte[] m52Csv = Files.readAllBytes(sample("results_m52_2025.csv"));
        Event m52 = createPublishedEvent("M52 Stage B", "m52-stage-b");
        importPublishedFixture(m52.getId(), "m52-full.csv", m52Csv);
        List<Long> removedRegistrationIds = jdbcTemplate.queryForList("""
                SELECT registration.id
                FROM registrations registration
                JOIN races race ON race.id=registration.race_id
                WHERE race.event_id=?
                ORDER BY registration.id DESC
                LIMIT 3
                """, Long.class, m52.getId());
        for (Long registrationId : removedRegistrationIds) {
            jdbcTemplate.update("DELETE FROM results WHERE registration_id=?", registrationId);
            jdbcTemplate.update("DELETE FROM registrations WHERE id=?", registrationId);
        }
        jdbcTemplate.update("UPDATE events SET result_data_revision=result_data_revision+1 WHERE id=?", m52.getId());
        List<Long> m52RaceIds = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(m52.getId()).stream()
                .map(Race::getId).toList();
        ImportPreviewResponseDto m52Preview = importPreviewService.preview(
                m52.getId(), "m52-partial.csv", m52Csv, ImportOperationMode.ADD_NEW,
                m52RaceIds, 0, ADMIN_USERNAME
        );
        assertThat(m52Preview.totals().newCount()).isEqualTo(3);
        ImportApplyResponseDto m52Applied = importApplyService.apply(
                m52.getId(), m52Preview.operationId(), m52Csv, ADMIN_USERNAME
        );
        assertThat(m52Applied.insertedCount()).isEqualTo(3);
        assertThat(registrationRepository.countByRaceEventId(m52.getId())).isEqualTo(31);
        assertThat(resultRepository.countByRegistrationRaceEventId(m52.getId())).isEqualTo(31);

        m52RaceIds.forEach(raceId -> raceResultsPublicationService.draft(
                m52.getId(), raceId, "Stage F real sample replacement", ADMIN_USERNAME
        ));
        ImportPreviewResponseDto m52EmergencyPreview = importPreviewService.preview(
                m52.getId(), "m52-corrected.csv", m52Csv, ImportOperationMode.EMERGENCY_REPLACE,
                m52RaceIds, 0, ADMIN_USERNAME
        );
        assertThat(m52EmergencyPreview.blockingErrorsPresent()).isFalse();
        assertThat(m52EmergencyPreview.emergencySummary().totals().currentCount()).isEqualTo(31);
        assertThat(m52EmergencyPreview.emergencySummary().totals().sourceCount()).isEqualTo(31);
        ImportApplyResponseDto m52EmergencyApplied = importApplyService.apply(
                m52.getId(), m52EmergencyPreview.operationId(), m52Csv, ADMIN_USERNAME
        );
        assertThat(m52EmergencyApplied.retiredCount()).isEqualTo(31);
        assertThat(m52EmergencyApplied.insertedCount()).isEqualTo(31);
        assertThat(registrationRepository.count()).isEqualTo(62);
        assertThat(resultRepository.count()).isEqualTo(62);
        assertThat(registrationRepository.countByRaceEventId(m52.getId())).isEqualTo(31);
        assertThat(resultRepository.countByRegistrationRaceEventId(m52.getId())).isEqualTo(31);

        byte[] heroCsv = Files.readAllBytes(sample("results_gonka2026.csv"));
        Event hero = createPublishedEvent("Hero Stage B", "hero-stage-b");
        importPublishedFixture(hero.getId(), "hero-full.csv", heroCsv);
        List<Long> heroRaceIds = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(hero.getId()).stream()
                .map(Race::getId).toList();
        ImportPreviewResponseDto heroPreview = importPreviewService.preview(
                hero.getId(), "hero.csv", heroCsv, ImportOperationMode.ADD_NEW,
                heroRaceIds, 0, ADMIN_USERNAME
        );
        assertThat(heroPreview.totals().duplicateCount()).isEqualTo(2);
        assertThatThrownBy(() -> importApplyService.apply(
                hero.getId(), heroPreview.operationId(), heroCsv, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_BLOCKED");
        assertThat(registrationRepository.countByRaceEventId(hero.getId())).isEqualTo(16);
        assertThat(resultRepository.countByRegistrationRaceEventId(hero.getId())).isEqualTo(16);
        heroRaceIds.forEach(raceId -> raceResultsPublicationService.draft(
                hero.getId(), raceId, "Stage F duplicate source check", ADMIN_USERNAME
        ));
        ImportPreviewResponseDto heroEmergencyPreview = importPreviewService.preview(
                hero.getId(), "hero-emergency.csv", heroCsv, ImportOperationMode.EMERGENCY_REPLACE,
                heroRaceIds, 0, ADMIN_USERNAME
        );
        assertThat(heroEmergencyPreview.totals().duplicateCount()).isEqualTo(2);
        assertThatThrownBy(() -> importApplyService.apply(
                hero.getId(), heroEmergencyPreview.operationId(), heroCsv, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_BLOCKED");
        assertThat(registrationRepository.countByRaceEventId(hero.getId())).isEqualTo(16);
        assertThat(resultRepository.countByRegistrationRaceEventId(hero.getId())).isEqualTo(16);
    }

    @Test
    void updatesExistingInPlaceCreatesMissingResultAndPreservesResultIssues() {
        Event event = createPublishedEvent("Stage C UPDATE", "stage-c-update");
        event.setStartsAt(Instant.parse("2026-06-01T06:00:00Z"));
        event = eventRepository.saveAndFlush(event);
        Long eventId = event.getId();
        importPublishedFixture(
                event.getId(), "initial.csv", stageCInitialCsv().getBytes(StandardCharsets.UTF_8));
        Race fiveKm = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        awardPolicyService.upsert(fiveKm.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 3, false,
                AgeCalculationMode.EVENT_DATE, 0, false), ADMIN_USERNAME);

        Map<String, Registration> registrations = registrationRepository.findAllCurrentByEventId(event.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(Registration::getBib, value -> value));
        Map<String, Long> registrationIds = registrations.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getId()));
        Map<String, Long> resultIds = registrations.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> resultRepository.findByRegistrationId(entry.getValue().getId()).orElseThrow().getId()
                ));

        Result missing = resultRepository.findById(resultIds.get("G-1")).orElseThrow();
        resultRepository.delete(missing);
        resultRepository.flush();
        ResultIssueRequest missingIssue = resultIssue(event, registrations.get("G-1"), null,
                ResultIssueType.MISSING_RESULT, null, null);
        Result correctionResult = resultRepository.findById(resultIds.get("H-1")).orElseThrow();
        ResultIssueRequest correctionIssue = resultIssue(event, registrations.get("H-1"), correctionResult,
                ResultIssueType.RESULT_CORRECTION, ResultCorrectionReason.OFFICIAL_TIME,
                correctionResult.getStatus());
        Map<String, Object> missingIssueSnapshot = resultIssueSnapshotState(missingIssue.getId());
        Map<String, Object> correctionIssueSnapshot = resultIssueSnapshotState(correctionIssue.getId());

        Map<String, Object> unchangedRegistration = jdbcTemplate.queryForMap(
                "SELECT * FROM registrations WHERE id=?", registrationIds.get("A-1"));
        Map<String, Object> unchangedResult = jdbcTemplate.queryForMap(
                "SELECT * FROM results WHERE id=?", resultIds.get("A-1"));
        long registrationCount = registrationRepository.countByRaceEventId(event.getId());
        long resultCount = resultRepository.countByRegistrationRaceEventId(event.getId());
        long revision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        byte[] csv = stageCUpdateCsv().getBytes(StandardCharsets.UTF_8);

        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "update.csv", csv, ImportOperationMode.UPDATE_EXISTING,
                List.of(fiveKm.getId()), 100, ADMIN_USERNAME
        );
        assertThat(preview.totals().changedCount()).isEqualTo(4);
        assertThat(preview.totals().unchangedCount()).isOne();
        assertThat(preview.totals().newCount()).isOne();
        assertThat(preview.totals().outOfScopeCount()).isOne();
        assertThat(preview.rows().stream().filter(row -> "G-1".equals(row.bib())).findFirst().orElseThrow())
                .satisfies(row -> {
                    assertThat(row.decision()).isEqualTo(ru.sportsresults.importing.ImportPreviewDecision.EXISTING_CHANGED);
                    assertThat(row.futureAction().name()).isEqualTo("CREATE_RESULT");
                    assertThat(row.matchedRegistrationId()).isEqualTo(registrationIds.get("G-1"));
                    assertThat(row.matchedResultId()).isNull();
                    assertThat(row.diffs()).extracting(ImportPreviewResponseDto.FieldDiff::field).contains("result");
                });

        ImportApplyResponseDto applied = importApplyService.apply(
                event.getId(), preview.operationId(), csv, ADMIN_USERNAME
        );
        assertThat(applied.insertedCount()).isZero();
        assertThat(applied.updatedCount()).isEqualTo(4);
        assertThat(applied.resultCreatedCount()).isOne();
        assertThat(applied.newSkippedCount()).isOne();
        assertThat(applied.existingSkippedCount()).isOne();
        assertThat(applied.unchangedCount()).isOne();
        assertThat(applied.outOfScopeCount()).isOne();
        assertThat(applied.newRevision()).isEqualTo(revision + 1);
        assertThat(registrationRepository.countByRaceEventId(event.getId())).isEqualTo(registrationCount);
        assertThat(resultRepository.countByRegistrationRaceEventId(event.getId())).isEqualTo(resultCount + 1);
        assertThat(registrationRepository.findAllByRaceIdAndBib(fiveKm.getId(), "NEW-1")).isEmpty();
        assertThat(jdbcTemplate.queryForMap(
                "SELECT * FROM registrations WHERE id=?", registrationIds.get("A-1")))
                .isEqualTo(unchangedRegistration);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT * FROM results WHERE id=?", resultIds.get("A-1")))
                .isEqualTo(unchangedResult);

        Registration changedPerson = registrationRepository.findById(registrationIds.get("C-1")).orElseThrow();
        assertThat(changedPerson.getLastName()).isEqualTo("Обновлённый");
        assertThat(changedPerson.getId()).isEqualTo(registrationIds.get("C-1"));
        Result changedResult = resultRepository.findByRegistrationId(changedPerson.getId()).orElseThrow();
        assertThat(changedResult.getId()).isEqualTo(resultIds.get("C-1"));
        assertThat(changedResult.getStatus()).isEqualTo("disqualified");
        assertThat(changedResult.getGunTime()).isNull();
        assertThat(changedResult.getChipTime()).isNull();
        assertThat(resultRepository.findByRegistrationId(registrationIds.get("B-1")).orElseThrow())
                .satisfies(result -> {
                    assertThat(result.getId()).isEqualTo(resultIds.get("B-1"));
                    assertThat(result.getGunTime()).isEqualTo(Duration.ofMillis(2_500));
                    assertThat(result.getChipTime()).isEqualTo(Duration.ofMillis(800));
                });
        Result createdResult = resultRepository.findByRegistrationId(registrationIds.get("G-1")).orElseThrow();
        assertThat(createdResult.getStatus()).isEqualTo("finished");
        assertThat(createdResult.getGunTime()).isEqualTo(Duration.ofMillis(3_500));

        assertThat(resultIssueRequestRepository.findById(correctionIssue.getId()).orElseThrow()).satisfies(issue -> {
            assertThat(issue.getStatus()).isEqualTo(ResultIssueStatus.NEW);
            assertThat(issue.getRegistration().getId()).isEqualTo(registrationIds.get("H-1"));
            assertThat(issue.getResult().getId()).isEqualTo(resultIds.get("H-1"));
            assertThat(issue.getQueueArchivedAt()).isNull();
        });
        assertThat(resultIssueRequestRepository.findById(missingIssue.getId()).orElseThrow()).satisfies(issue -> {
            assertThat(issue.getStatus()).isEqualTo(ResultIssueStatus.NEW);
            assertThat(issue.getRegistration().getId()).isEqualTo(registrationIds.get("G-1"));
            assertThat(issue.getResult()).isNull();
            assertThat(issue.getQueueArchivedAt()).isNull();
        });
        assertThat(resultIssueSnapshotState(missingIssue.getId())).isEqualTo(missingIssueSnapshot);
        assertThat(resultIssueSnapshotState(correctionIssue.getId())).isEqualTo(correctionIssueSnapshot);
        assertThat(importOperationItemRepository.findAllByOperationIdOrderBySourceRowNumberAsc(preview.operationId()))
                .extracting(item -> item.getDecision().name() + ":" + item.getAction().name())
                .containsExactly(
                        "EXISTING_UNCHANGED:SKIP", "EXISTING_CHANGED:UPDATE", "EXISTING_CHANGED:UPDATE",
                        "EXISTING_CHANGED:CREATE_RESULT", "EXISTING_CHANGED:UPDATE", "NEW:SKIP", "OUT_OF_SCOPE:SKIP"
                );
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ru.sportsresults.domain.ResultsPublicationStatus.PUBLISHED);

        ImportApplyResponseDto retry = importApplyService.apply(
                event.getId(), preview.operationId(), csv, ADMIN_USERNAME
        );
        assertThat(retry.importBatchId()).isEqualTo(applied.importBatchId());
        assertThat(retry.newRevision()).isEqualTo(applied.newRevision());
        assertThat(resultRepository.findByRegistrationId(registrationIds.get("G-1")).orElseThrow().getId())
                .isEqualTo(createdResult.getId());

        var gunStanding = resultQueryService.search(
                event.getId(), fiveKm.getId(), null, null, null, null, null,
                0, 20, "place", "asc"
        );
        assertThat(achievement(gunStanding.content(), "Дарья Обращение", "ABSOLUTE").place()).isEqualTo(2);
        assertThat(achievement(gunStanding.content(), "Борис Ручной", "ABSOLUTE").place()).isEqualTo(3);
        assertThat(item(gunStanding.content(), "Вера Обновлённый").rankingAchievements()).isEmpty();

        awardPolicyService.upsert(fiveKm.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.BY_GENDER, 3, false,
                AgeCalculationMode.EVENT_DATE, 0, false), ADMIN_USERNAME);
        var chipGenderStanding = resultQueryService.search(
                event.getId(), fiveKm.getId(), null, null, null, null, null,
                0, 20, "place", "asc"
        );
        assertThat(achievement(chipGenderStanding.content(), "Борис Ручной", "GENDER").place()).isEqualTo(1);
        assertThat(achievement(chipGenderStanding.content(), "Альфа Без изменений", "GENDER").place()).isEqualTo(2);
        assertThat(achievement(chipGenderStanding.content(), "Дарья Обращение", "GENDER").place()).isEqualTo(3);

        ImportPreviewResponseDto sameSha = importPreviewService.preview(
                event.getId(), "update-again.csv", csv, ImportOperationMode.UPDATE_EXISTING,
                List.of(fiveKm.getId()), 100, ADMIN_USERNAME
        );
        assertThat(sameSha.fileSha256()).isEqualTo(preview.fileSha256());
        assertThat(sameSha.totals().changedCount()).isZero();
        long batchCountBeforeNoOp = importBatchRepository.count();
        long registrationCountBeforeNoOp = registrationRepository.countByRaceEventId(event.getId());
        long resultCountBeforeNoOp = resultRepository.countByRegistrationRaceEventId(event.getId());
        long revisionBeforeNoOp = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        ImportApplyResponseDto noOp = importApplyService.apply(
                eventId, sameSha.operationId(), csv, ADMIN_USERNAME
        );
        assertThat(noOp.noOp()).isTrue();
        assertThat(noOp.status()).isEqualTo(ImportOperationStatus.PREVIEWED);
        assertThat(noOp.importBatchId()).isNull();
        assertThat(noOp.updatedCount()).isZero();
        assertThat(noOp.unchangedCount()).isEqualTo(sameSha.totals().unchangedCount());
        assertThat(noOp.newRevision()).isEqualTo(revisionBeforeNoOp);
        assertThat(noOp.appliedAt()).isNull();
        assertThat(importBatchRepository.count()).isEqualTo(batchCountBeforeNoOp);
        assertThat(registrationRepository.countByRaceEventId(event.getId()))
                .isEqualTo(registrationCountBeforeNoOp);
        assertThat(resultRepository.countByRegistrationRaceEventId(event.getId()))
                .isEqualTo(resultCountBeforeNoOp);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revisionBeforeNoOp);
    }

    @Test
    void movesRaceClusterAndEffectiveCategoryInPlaceAndBlocksUnsafeMoves() {
        Event event = createPublishedEvent("Stage C move", "stage-c-move");
        event.setStartsAt(Instant.parse("2026-06-01T06:00:00Z"));
        event.setTimeZone("UTC");
        event = eventRepository.saveAndFlush(event);
        importPublishedFixture(
                event.getId(), "move-initial.csv", stageCRaceMoveInitialCsv().getBytes(StandardCharsets.UTF_8));
        Race raceA = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        Race raceB = raceRepository.findByEventIdAndSourceCode(event.getId(), "10 km").orElseThrow();
        var clusterA = startClusterService.create(event.getId(), raceA.getId(),
                new ru.sportsresults.api.dto.UpsertStartClusterRequest("A", null, "Cluster A", 0, null),
                ADMIN_USERNAME);
        var clusterB = startClusterService.create(event.getId(), raceB.getId(),
                new ru.sportsresults.api.dto.UpsertStartClusterRequest("B", null, "Cluster B", 0, null),
                ADMIN_USERNAME);
        Category categoryA = categoryRepository.findByRaceIdAndSourceName(raceA.getId(), "A30").orElseThrow();
        categoryA.setDisplayName("30-39 A");
        categoryA.setMinAge(30);
        categoryA.setMaxAge(39);
        categoryA.setGender(CategoryGender.MALE);
        Category categoryB = categoryRepository.findByRaceIdAndSourceName(raceB.getId(), "B30").orElseThrow();
        categoryB.setDisplayName("30-39 B");
        categoryB.setMinAge(30);
        categoryB.setMaxAge(39);
        categoryB.setGender(CategoryGender.MALE);
        categoryRepository.saveAllAndFlush(List.of(categoryA, categoryB));

        Registration moving = registrationRepository.findAllByRaceIdAndBib(raceA.getId(), "MOVE-1").getFirst();
        Result movingResult = resultRepository.findByRegistrationId(moving.getId()).orElseThrow();
        adminResultService.updateRegistration(moving.getId(), new ru.sportsresults.api.dto.UpdateRegistrationRequest(
                moving.getDisplayName(), moving.getFirstName(), moving.getLastName(), moving.getBirthDate(),
                moving.getGender(), moving.getBib(), moving.getSourceCategory(), clusterA.id(), moving.getEntryKind(),
                moving.getCategory() == null ? null : moving.getCategory().getId()
        ), ADMIN_USERNAME);
        moving = registrationRepository.findById(moving.getId()).orElseThrow();
        moving.setCategory(categoryA);
        registrationRepository.saveAndFlush(moving);
        Long registrationId = moving.getId();
        Long resultId = movingResult.getId();

        List<Long> bothRaces = List.of(raceA.getId(), raceB.getId());
        assertMoveBlocked(event, bothRaces, stageCRaceMoveWithoutDobCsv(), "RACE_MOVE_REQUIRES_DOB");
        assertMoveBlocked(event, bothRaces, stageCRaceMoveCsv("1991-01-01", "B"), "RACE_MOVE_DOB_MISMATCH");
        assertMoveBlocked(event, bothRaces, stageCRaceMoveCsv("", "B"), "RACE_MOVE_REQUIRES_DOB");
        assertMoveBlocked(event, bothRaces, stageCRaceMoveWithoutClusterCsv(), "RACE_MOVE_CLUSTER_REQUIRED");
        ImportPreviewResponseDto raceScopedCluster = importPreviewService.preview(
                event.getId(), "race-scoped-cluster.csv",
                stageCRaceMoveCsv("1990-01-01", "A").getBytes(StandardCharsets.UTF_8),
                ImportOperationMode.UPDATE_EXISTING, bothRaces, 100, ADMIN_USERNAME
        );
        assertThat(raceScopedCluster.blockingErrorsPresent()).isFalse();
        assertThat(raceScopedCluster.rows().getFirst().diffs())
                .extracting(ImportPreviewResponseDto.FieldDiff::field)
                .contains("cluster", "clusterDefinition");
        assertThat(startClusterRepository.countByRaceId(raceB.getId())).isOne();
        assertMoveBlocked(event, List.of(raceA.getId()), stageCRaceMoveCsv("1990-01-01", "B"),
                "TARGET_RACE_OUT_OF_SCOPE");
        ImportPreviewResponseDto currentOutOfScope = importPreviewService.preview(
                event.getId(), "current-out.csv", stageCRaceMoveCsv("1990-01-01", "B").getBytes(StandardCharsets.UTF_8),
                ImportOperationMode.UPDATE_EXISTING, List.of(raceB.getId()), 100, ADMIN_USERNAME
        );
        assertThat(currentOutOfScope.rows().getFirst().reasonCode()).isEqualTo("CURRENT_RACE_OUT_OF_SCOPE");
        assertThat(currentOutOfScope.blockingErrorsPresent()).isTrue();

        byte[] csv = stageCRaceMoveCsv("1990-01-01", "B").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "move.csv", csv, ImportOperationMode.UPDATE_EXISTING,
                List.of(raceA.getId(), raceB.getId()), 100, ADMIN_USERNAME
        );
        assertThat(preview.rows()).singleElement().satisfies(row -> {
            assertThat(row.decision()).isEqualTo(ru.sportsresults.importing.ImportPreviewDecision.EXISTING_CHANGED);
            assertThat(row.diffs()).extracting(ImportPreviewResponseDto.FieldDiff::field)
                    .contains("race", "cluster", "sourceCategory", "effectiveCategory");
        });
        ImportApplyResponseDto applied = importApplyService.apply(
                event.getId(), preview.operationId(), csv, ADMIN_USERNAME
        );
        assertThat(applied.updatedCount()).isOne();
        Registration moved = registrationRepository.findById(registrationId).orElseThrow();
        assertThat(moved.getRace().getId()).isEqualTo(raceB.getId());
        assertThat(moved.getCluster().getId()).isEqualTo(clusterB.id());
        assertThat(moved.getCategory().getId()).isEqualTo(categoryB.getId());
        assertThat(moved.getSourceCategory()).isEqualTo("B30");
        assertThat(resultRepository.findByRegistrationId(registrationId).orElseThrow().getId()).isEqualTo(resultId);
        assertThat(resultQueryService.search(
                event.getId(), raceA.getId(), null, "MOVE-1", null, null, null,
                0, 10, "place", "asc").content()).isEmpty();
        assertThat(resultQueryService.search(
                event.getId(), raceB.getId(), null, "MOVE-1", null, null, null,
                0, 10, "place", "asc").content()).singleElement();
    }

    @Test
    void preservesAbsentFieldsBlocksClusterClearAndAuditsCategoryCreation() {
        Event event = createPublishedEvent("Stage C fields", "stage-c-fields");
        event.setStartsAt(Instant.parse("2026-06-01T06:00:00Z"));
        event.setTimeZone("UTC");
        event = eventRepository.saveAndFlush(event);
        Long eventId = event.getId();
        importPublishedFixture(
                event.getId(), "initial.csv", oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        var cluster = startClusterService.create(event.getId(), race.getId(),
                new ru.sportsresults.api.dto.UpsertStartClusterRequest("A", null, "Cluster A", 0, null),
                ADMIN_USERNAME);
        Registration registration = registrationRepository.findAllByRaceIdAndBib(race.getId(), "A-1").getFirst();
        adminResultService.updateRegistration(registration.getId(), new ru.sportsresults.api.dto.UpdateRegistrationRequest(
                registration.getDisplayName(), registration.getFirstName(), registration.getLastName(),
                registration.getBirthDate(), registration.getGender(), registration.getBib(),
                registration.getSourceCategory(), cluster.id(), registration.getEntryKind(),
                registration.getCategory() == null ? null : registration.getCategory().getId()
        ), ADMIN_USERNAME);
        registration = registrationRepository.findById(registration.getId()).orElseThrow();
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();

        byte[] emptyCluster = stageCAbsentFieldsCsv("Пустой", true).getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto blocked = importPreviewService.preview(
                event.getId(), "empty-cluster.csv", emptyCluster, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(blocked.blockingErrorsPresent()).isTrue();
        assertThat(blocked.rows().getFirst().reasonCode()).isEqualTo("START_CLUSTER_CLEAR_NOT_ALLOWED");
        long blockedRevision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        assertThatThrownBy(() -> importApplyService.apply(
                eventId, blocked.operationId(), emptyCluster, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_BLOCKED");
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(blockedRevision);

        byte[] absent = stageCAbsentFieldsCsv("Сохранённый", false).getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "absent.csv", absent, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(preview.rows().getFirst().diffs()).extracting(ImportPreviewResponseDto.FieldDiff::field)
                .containsExactlyInAnyOrder("displayName", "lastName");
        importApplyService.apply(event.getId(), preview.operationId(), absent, ADMIN_USERNAME);
        Registration updated = registrationRepository.findById(registration.getId()).orElseThrow();
        Result preserved = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();
        assertThat(updated.getLastName()).isEqualTo("Сохранённый");
        assertThat(updated.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(updated.getGender()).isEqualTo("male");
        assertThat(updated.getSourceCategory()).isEqualTo("Open");
        assertThat(updated.getCluster().getId()).isEqualTo(cluster.id());
        assertThat(preserved.getId()).isEqualTo(result.getId());
        assertThat(preserved.getGunTime()).isEqualTo(Duration.ofMillis(1_000));
        assertThat(preserved.getChipTime()).isEqualTo(Duration.ofMillis(900));

        byte[] clearTimes = stageCClearTimesCsv("Сохранённый").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto clearPreview = importPreviewService.preview(
                event.getId(), "clear-times.csv", clearTimes, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(clearPreview.rows().getFirst().diffs()).extracting(ImportPreviewResponseDto.FieldDiff::field)
                .contains("gunTimeMs", "chipTimeMs");
        importApplyService.apply(event.getId(), clearPreview.operationId(), clearTimes, ADMIN_USERNAME);
        assertThat(resultRepository.findByRegistrationId(registration.getId()).orElseThrow()).satisfies(cleared -> {
            assertThat(cleared.getGunTime()).isNull();
            assertThat(cleared.getChipTime()).isNull();
            assertThat(cleared.getId()).isEqualTo(result.getId());
        });

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 3, true,
                AgeCalculationMode.EVENT_DATE, 2, false), ADMIN_USERNAME);
        Map<String, Object> policyBefore = jdbcTemplate.queryForMap(
                "SELECT * FROM award_policies WHERE race_id=?", race.getId());
        byte[] categoryCsv = stageCCategoryCreationCsv("Сохранённый").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto categoryPreview = importPreviewService.preview(
                event.getId(), "category.csv", categoryCsv, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(categoryPreview.rows().getFirst().diffs()).extracting(ImportPreviewResponseDto.FieldDiff::field)
                .contains("birthDate", "sourceCategory", "categoryDefinition", "effectiveCategory");
        importApplyService.apply(event.getId(), categoryPreview.operationId(), categoryCsv, ADMIN_USERNAME);
        Registration categorized = registrationRepository.findById(registration.getId()).orElseThrow();
        assertThat(categorized.getBirthDate()).isNull();
        assertThat(categorized.getSourceCategory()).isEqualTo("New Source Category");
        assertThat(categorized.getCategory().getSourceName()).isEqualTo("New Source Category");
        assertThat(categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(race.getId()))
                .filteredOn(category -> category.getSourceName().equals("New Source Category"))
                .hasSize(1);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT * FROM award_policies WHERE race_id=?", race.getId())).isEqualTo(policyBefore);
    }

    @Test
    void serializesUpdateApplyAndRejectsStaleDigestAndFileMismatch() throws Exception {
        Event event = createPublishedEvent("Stage C concurrency", "stage-c-concurrency");
        importPublishedFixture(
                event.getId(), "initial.csv", oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        Registration registration = registrationRepository.findAllByRaceIdAndBib(race.getId(), "A-1").getFirst();
        Long resultId = resultRepository.findByRegistrationId(registration.getId()).orElseThrow().getId();
        byte[] update = oneRowCsv("2000.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto sameOperation = importPreviewService.preview(
                event.getId(), "same.csv", update, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ImportApplyResponseDto> apply = () -> {
            start.await(10, TimeUnit.SECONDS);
            return importApplyService.apply(event.getId(), sameOperation.operationId(), update, ADMIN_USERNAME);
        };
        var first = pool.submit(apply);
        var second = pool.submit(apply);
        start.countDown();
        ImportApplyResponseDto firstResponse = first.get(30, TimeUnit.SECONDS);
        ImportApplyResponseDto secondResponse = second.get(30, TimeUnit.SECONDS);
        assertThat(firstResponse.importBatchId()).isEqualTo(secondResponse.importBatchId());
        assertThat(firstResponse.newRevision()).isEqualTo(secondResponse.newRevision());
        assertThat(firstResponse.updatedCount()).isOne();
        assertThat(resultRepository.findByRegistrationId(registration.getId()).orElseThrow()).satisfies(current -> {
            assertThat(current.getId()).isEqualTo(resultId);
            assertThat(current.getGunTime()).isEqualTo(Duration.ofMillis(2_000));
        });

        byte[] updateA = oneRowCsv("3000.0").getBytes(StandardCharsets.UTF_8);
        byte[] updateB = oneRowCsv("4000.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto operationA = importPreviewService.preview(
                event.getId(), "a.csv", updateA, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        ImportPreviewResponseDto operationB = importPreviewService.preview(
                event.getId(), "b.csv", updateB, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), operationB.operationId(), updateA, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("FILE_MISMATCH");
        importApplyService.apply(event.getId(), operationA.operationId(), updateA, ADMIN_USERNAME);
        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), operationB.operationId(), updateB, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_STALE");

        byte[] updateDigest = oneRowCsv("5000.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto digest = importPreviewService.preview(
                event.getId(), "digest.csv", updateDigest, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        jdbcTemplate.update("UPDATE import_operations SET plan_digest=repeat('e', 64) WHERE id=?",
                digest.operationId());
        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), digest.operationId(), updateDigest, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_STALE");
        pool.shutdownNow();
    }

    @Test
    void rollsBackAllUpdateMutationsAndKeepsIssueLinksOnLateFailure() {
        Event event = createPublishedEvent("Stage C rollback", "stage-c-rollback");
        importPublishedFixture(
                event.getId(), "initial.csv", oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        Registration registration = registrationRepository.findAllByRaceIdAndBib(race.getId(), "A-1").getFirst();
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();
        ResultIssueRequest issue = resultIssue(event, registration, result, ResultIssueType.RESULT_CORRECTION,
                ResultCorrectionReason.OFFICIAL_TIME, result.getStatus());
        byte[] update = stageCRollbackCsv().getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "rollback.csv", update, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        jdbcTemplate.update("""
                INSERT INTO import_operation_items(
                    operation_id, source_row_number, bib, decision, action, registration_id, result_id,
                    target_race_id, created_at, updated_at
                ) VALUES (?, 2, 'A-1', 'EXISTING_CHANGED', 'UPDATE', ?, ?, ?, now(), now())
                """, preview.operationId(), registration.getId(), result.getId(), race.getId());
        Map<String, Object> registrationBefore = jdbcTemplate.queryForMap(
                "SELECT * FROM registrations WHERE id=?", registration.getId());
        Map<String, Object> resultBefore = jdbcTemplate.queryForMap(
                "SELECT * FROM results WHERE id=?", result.getId());
        long batchesBefore = importBatchRepository.count();
        long revisionBefore = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();

        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), preview.operationId(), update, ADMIN_USERNAME
        )).isInstanceOf(RuntimeException.class);

        assertThat(jdbcTemplate.queryForMap(
                "SELECT * FROM registrations WHERE id=?", registration.getId())).isEqualTo(registrationBefore);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT * FROM results WHERE id=?", result.getId())).isEqualTo(resultBefore);
        assertThat(importBatchRepository.count()).isEqualTo(batchesBefore);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revisionBefore);
        assertThat(importOperationRepository.findById(preview.operationId()).orElseThrow().getStatus())
                .isEqualTo(ImportOperationStatus.PREVIEWED);
        assertThat(resultIssueRequestRepository.findById(issue.getId()).orElseThrow()).satisfies(current -> {
            assertThat(current.getStatus()).isEqualTo(ResultIssueStatus.NEW);
            assertThat(current.getRegistration().getId()).isEqualTo(registration.getId());
            assertThat(current.getResult().getId()).isEqualTo(result.getId());
        });
    }

    @Test
    void updatesOnlyControlledSyntheticM52DifferencesAndBlocksSyntheticHeroDuplicateBib() throws Exception {
        byte[] m52Csv = Files.readAllBytes(sample("results_m52_2025.csv"));
        Event m52 = createPublishedEvent("M52 Stage C", "m52-stage-c");
        importPublishedFixture(m52.getId(), "m52-full.csv", m52Csv);
        List<Long> registrationIds = jdbcTemplate.queryForList("""
                SELECT registration.id FROM registrations registration
                JOIN races race ON race.id=registration.race_id
                WHERE race.event_id=? ORDER BY registration.id LIMIT 3
                """, Long.class, m52.getId());
        Long resultToChange = jdbcTemplate.queryForObject(
                "SELECT id FROM results WHERE registration_id=?", Long.class, registrationIds.get(0));
        Long originalGunTime = jdbcTemplate.queryForObject(
                "SELECT gun_time_ms FROM results WHERE id=?", Long.class, resultToChange);
        jdbcTemplate.update("UPDATE results SET gun_time_ms=gun_time_ms+777 WHERE id=?", resultToChange);
        jdbcTemplate.update("UPDATE registrations SET last_name='CONTROLLED-DIFFERENCE' WHERE id=?",
                registrationIds.get(1));
        jdbcTemplate.update("DELETE FROM results WHERE registration_id=?", registrationIds.get(2));
        jdbcTemplate.update("DELETE FROM registrations WHERE id=?", registrationIds.get(2));
        jdbcTemplate.update("UPDATE events SET result_data_revision=result_data_revision+1 WHERE id=?", m52.getId());
        List<Long> raceIds = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(m52.getId()).stream()
                .map(Race::getId).toList();

        ImportPreviewResponseDto preview = importPreviewService.preview(
                m52.getId(), "m52-update.csv", m52Csv, ImportOperationMode.UPDATE_EXISTING,
                raceIds, 0, ADMIN_USERNAME
        );
        assertThat(preview.totals().changedCount()).isEqualTo(2);
        assertThat(preview.totals().newCount()).isOne();
        ImportApplyResponseDto applied = importApplyService.apply(
                m52.getId(), preview.operationId(), m52Csv, ADMIN_USERNAME
        );
        assertThat(applied.updatedCount()).isEqualTo(2);
        assertThat(applied.newSkippedCount()).isOne();
        assertThat(applied.insertedCount()).isZero();
        assertThat(registrationRepository.countByRaceEventId(m52.getId())).isEqualTo(30);
        assertThat(resultRepository.countByRegistrationRaceEventId(m52.getId())).isEqualTo(30);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT gun_time_ms FROM results WHERE id=?", Long.class, resultToChange)).isEqualTo(originalGunTime);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_name FROM registrations WHERE id=?", String.class, registrationIds.get(1)))
                .isNotEqualTo("CONTROLLED-DIFFERENCE");

        byte[] heroCsv = Files.readAllBytes(sample("results_gonka2026.csv"));
        Event hero = createPublishedEvent("Hero Stage C", "hero-stage-c");
        importPublishedFixture(hero.getId(), "hero-full.csv", heroCsv);
        List<Long> heroRaceIds = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(hero.getId()).stream()
                .map(Race::getId).toList();
        ImportPreviewResponseDto heroPreview = importPreviewService.preview(
                hero.getId(), "hero-update.csv", heroCsv, ImportOperationMode.UPDATE_EXISTING,
                heroRaceIds, 0, ADMIN_USERNAME
        );
        assertThat(heroPreview.totals().duplicateCount()).isEqualTo(2);
        assertThatThrownBy(() -> importApplyService.apply(
                hero.getId(), heroPreview.operationId(), heroCsv, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_BLOCKED");
        assertThat(registrationRepository.countByRaceEventId(hero.getId())).isEqualTo(16);
        assertThat(resultRepository.countByRegistrationRaceEventId(hero.getId())).isEqualTo(16);
    }

    @Test
    void configuresValidatesAndAuditsEventResultInquirySettings() throws Exception {
        Event event = createPublishedEvent("Result inquiry settings", "result-inquiry-settings");
        event.setStartsAt(Instant.now().minus(Duration.ofDays(2)));
        event.setEndsAt(Instant.now().minus(Duration.ofDays(1)));
        event = eventRepository.saveAndFlush(event);
        Long eventId = event.getId();

        assertThat(event.isResultInquiryEnabled()).isFalse();
        assertThat(event.getResultInquiryWindowDays()).isNull();
        assertThat(event.getResultInquiryEmail()).isNull();
        mockMvc.perform(get("/api/admin/events/{eventId}/result-inquiry", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.windowDays").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.availability").value("DISABLED"))
                .andExpect(jsonPath("$.deadline").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(put("/api/admin/events/{eventId}/result-inquiry", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"windowDays\":0,\"email\":\"timing@example.org\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/events/{eventId}/result-inquiry", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"windowDays\":5}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/events/{eventId}/result-inquiry", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"windowDays\":5,\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> eventService.updateResultInquirySettings(
                eventId, new UpdateResultInquirySettingsRequest(true, -1, "timing@example.org"), ADMIN_USERNAME
        )).isInstanceOf(InvalidRequestException.class).hasMessageContaining("greater than zero");

        mockMvc.perform(put("/api/admin/events/{eventId}/result-inquiry", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"windowDays\":5,\"email\":\"timing@example.org\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.windowDays").value(5))
                .andExpect(jsonPath("$.email").value("timing@example.org"))
                .andExpect(jsonPath("$.availability").value("OPEN"))
                .andExpect(jsonPath("$.deadline").isString());

        Event configured = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(configured.isResultInquiryEnabled()).isTrue();
        assertThat(configured.getResultInquiryWindowDays()).isEqualTo(5);
        assertThat(configured.getResultInquiryEmail()).isEqualTo("timing@example.org");
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.EVENT, event.getId()
        )).extracting(AdminChangeLog::getFieldName)
                .containsExactlyInAnyOrder(
                        "resultInquiryEnabled", "resultInquiryWindowDays", "resultInquiryEmail"
                );

        mockMvc.perform(put("/api/admin/events/{eventId}/result-inquiry", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"windowDays\":null,\"email\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void createsEventWithResultIssueSettingsAndRecalculatesDeadlineOnUpdate() throws Exception {
        EventSeries series = createSeries("Submission settings", "submission-settings-series");
        Instant startsAt = Instant.now().minus(Duration.ofDays(2))
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant endsAt = Instant.now().minus(Duration.ofDays(1))
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String payload = objectMapper.writeValueAsString(Map.of(
                "eventSeriesId", series.getId(),
                "name", "Submission settings Event",
                "startsAt", startsAt,
                "endsAt", endsAt,
                "timeZone", "Europe/Moscow",
                "publicationStatus", "DRAFT",
                "resultInquiry", Map.of(
                        "enabled", true,
                        "windowDays", 3,
                        "email", "timing@example.org"
                )
        ));

        MvcResult created = mockMvc.perform(post("/api/admin/events")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn();
        long eventId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();
        Instant threeDayDeadline = endsAt.atZone(java.time.ZoneId.of("Europe/Moscow"))
                .toLocalDate().plusDays(3).atTime(java.time.LocalTime.MAX)
                .atZone(java.time.ZoneId.of("Europe/Moscow")).toInstant();

        mockMvc.perform(get("/api/admin/events/{eventId}/result-inquiry", eventId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.deadlineMode").value("AFTER_EVENT_DAYS"))
                .andExpect(jsonPath("$.windowDays").value(3))
                .andExpect(jsonPath("$.fixedDate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.availability").value("OPEN"))
                .andExpect(jsonPath("$.deadline").value(threeDayDeadline.toString()));

        Instant sevenDayDeadline = endsAt.atZone(java.time.ZoneId.of("Europe/Moscow"))
                .toLocalDate().plusDays(7).atTime(java.time.LocalTime.MAX)
                .atZone(java.time.ZoneId.of("Europe/Moscow")).toInstant();
        mockMvc.perform(put("/api/admin/events/{eventId}/result-inquiry", eventId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"windowDays\":7,\"email\":\"timing@example.org\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowDays").value(7))
                .andExpect(jsonPath("$.deadline").value(sevenDayDeadline.toString()));
    }

    @Test
    void copiesTemplateInquiryDefaultsOnceAndAllowsEventOverride() throws Exception {
        EventSeries series = createSeries("Inquiry defaults", "inquiry-defaults-series");
        String fiveDayDefaults = objectMapper.writeValueAsString(Map.of(
                "name", series.getName(),
                "slug", series.getSlug(),
                "active", true,
                "resultInquiryDefaults", Map.of(
                        "enabled", true,
                        "deadlineMode", "AFTER_EVENT_DAYS",
                        "windowDays", 5,
                        "email", "template@example.org"
                )
        ));
        mockMvc.perform(put("/api/admin/event-series/{seriesId}", series.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(fiveDayDefaults))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultInquiryDefaults.windowDays").value(5));

        String fromTemplate = objectMapper.writeValueAsString(Map.of(
                "eventSeriesId", series.getId(),
                "name", "Copied defaults event",
                "startsAt", "2027-08-15T08:00:00Z",
                "endsAt", "2027-08-15T13:00:00Z",
                "timeZone", "Asia/Yekaterinburg",
                "publicationStatus", "DRAFT"
        ));
        MvcResult copiedResult = mockMvc.perform(post("/api/admin/events")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(fromTemplate))
                .andExpect(status().isCreated()).andReturn();
        long copiedEventId = objectMapper.readTree(
                copiedResult.getResponse().getContentAsByteArray()).get("id").asLong();
        mockMvc.perform(get("/api/admin/events/{eventId}/result-inquiry", copiedEventId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.deadlineMode").value("AFTER_EVENT_DAYS"))
                .andExpect(jsonPath("$.windowDays").value(5))
                .andExpect(jsonPath("$.email").value("template@example.org"));

        String tenDayDefaults = fiveDayDefaults
                .replace("\"windowDays\":5", "\"windowDays\":10")
                .replace("template@example.org", "changed-template@example.org");
        mockMvc.perform(put("/api/admin/event-series/{seriesId}", series.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(tenDayDefaults))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultInquiryDefaults.windowDays").value(10))
                .andExpect(jsonPath("$.resultInquiryDefaults.email").value("changed-template@example.org"));
        mockMvc.perform(get("/api/admin/events/{eventId}/result-inquiry", copiedEventId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowDays").value(5))
                .andExpect(jsonPath("$.email").value("template@example.org"));
        Event copiedEvent = eventRepository.findById(copiedEventId).orElseThrow();
        copiedEvent.setStartsAt(Instant.now().minus(Duration.ofDays(2)));
        copiedEvent.setEndsAt(Instant.now().minus(Duration.ofDays(1)));
        copiedEvent.setPublicationStatus(EventPublicationStatus.PUBLISHED);
        copiedEvent.setResultsPublicationStatus(ru.sportsresults.domain.ResultsPublicationStatus.PUBLISHED);
        eventRepository.saveAndFlush(copiedEvent);
        importPublishedFixture(
                copiedEventId, "copied-template-fallback.csv",
                singleInquiryBibCsv("Copied", "finished").getBytes(StandardCharsets.UTF_8)
        );
        assertThat(resultInquiryService.verify(
                copiedEventId, "1100", LocalDate.parse("1980-01-01")
        ).contactEmail()).isEqualTo("template@example.org");

        Map<String, Object> overridePayload = new LinkedHashMap<>();
        overridePayload.put("eventSeriesId", series.getId());
        overridePayload.put("name", "Overridden defaults event");
        overridePayload.put("startsAt", "2027-08-16T08:00:00Z");
        overridePayload.put("endsAt", "2027-08-16T13:00:00Z");
        overridePayload.put("timeZone", "Asia/Yekaterinburg");
        overridePayload.put("publicationStatus", "DRAFT");
        overridePayload.put("resultInquiry", Map.of(
                "enabled", true,
                "deadlineMode", "AFTER_EVENT_DAYS",
                "windowDays", 7,
                "email", "override@example.org"
        ));
        MvcResult overriddenResult = mockMvc.perform(post("/api/admin/events")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overridePayload)))
                .andExpect(status().isCreated()).andReturn();
        long overriddenEventId = objectMapper.readTree(
                overriddenResult.getResponse().getContentAsByteArray()).get("id").asLong();
        mockMvc.perform(get("/api/admin/events/{eventId}/result-inquiry", overriddenEventId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowDays").value(7))
                .andExpect(jsonPath("$.email").value("override@example.org"));

        String bulk = objectMapper.writeValueAsString(Map.of(
                "eventSeriesId", series.getId(),
                "date", "2027-09-01",
                "events", List.of(
                        Map.of("name", "Bulk Inquiry A", "location", "Bulk Inquiry A",
                                "timeZone", "Europe/Moscow"),
                        Map.of("name", "Bulk Inquiry B", "location", "Bulk Inquiry B",
                                "timeZone", "Asia/Yekaterinburg")
                )
        ));
        mockMvc.perform(post("/api/admin/events/bulk")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(bulk))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.events.length()").value(2));
        assertThat(eventRepository.findAllByEventSeriesIdOrderByIdAsc(series.getId()).stream()
                .filter(created -> created.getName().startsWith("Bulk Inquiry")).toList())
                .allSatisfy(created -> {
                    assertThat(created.isResultInquiryEnabled()).isTrue();
                    assertThat(created.getResultInquiryDeadlineMode())
                            .isEqualTo(ResultInquiryDeadlineMode.AFTER_EVENT_DAYS);
                    assertThat(created.getResultInquiryWindowDays()).isEqualTo(10);
                    assertThat(created.getResultInquiryEmail()).isEqualTo("changed-template@example.org");
                });
    }

    @Test
    void copiesFixedDateTemplateDefaultAndRejectsInvalidEventAndBulkDates() throws Exception {
        EventSeries series = createSeries("Fixed inquiry defaults", "fixed-inquiry-defaults-series");
        String defaults = objectMapper.writeValueAsString(Map.of(
                "name", series.getName(),
                "slug", series.getSlug(),
                "active", true,
                "resultInquiryDefaults", Map.of(
                        "enabled", true,
                        "deadlineMode", "FIXED_DATE",
                        "fixedDate", "2027-08-20",
                        "email", "fixed@example.org"
                )
        ));
        mockMvc.perform(put("/api/admin/event-series/{seriesId}", series.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(defaults))
                .andExpect(status().isOk());

        String validEvent = objectMapper.writeValueAsString(Map.of(
                "eventSeriesId", series.getId(),
                "name", "Valid fixed date event",
                "startsAt", "2027-08-15T08:00:00Z",
                "endsAt", "2027-08-15T13:00:00Z",
                "timeZone", "Asia/Yekaterinburg",
                "publicationStatus", "DRAFT"
        ));
        MvcResult validResult = mockMvc.perform(post("/api/admin/events")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(validEvent))
                .andExpect(status().isCreated()).andReturn();
        long eventId = objectMapper.readTree(validResult.getResponse().getContentAsByteArray()).get("id").asLong();
        mockMvc.perform(get("/api/admin/events/{eventId}/result-inquiry", eventId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deadlineMode").value("FIXED_DATE"))
                .andExpect(jsonPath("$.fixedDate").value("2027-08-20"))
                .andExpect(jsonPath("$.deadline").value("2027-08-20T18:59:59.999999999Z"));

        String invalidEvent = validEvent.replace("Valid fixed date event", "Invalid fixed date event")
                .replace("2027-08-15T08:00:00Z", "2027-08-21T08:00:00Z")
                .replace("2027-08-15T13:00:00Z", "2027-08-21T13:00:00Z");
        mockMvc.perform(post("/api/admin/events")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(invalidEvent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESULT_INQUIRY_FIXED_DATE_BEFORE_EVENT"));

        String bulk = objectMapper.writeValueAsString(Map.of(
                "eventSeriesId", series.getId(),
                "date", "2027-08-21",
                "events", List.of(Map.of(
                        "name", "Invalid bulk city",
                        "location", "Invalid bulk city",
                        "timeZone", "Asia/Yekaterinburg"
                ))
        ));
        mockMvc.perform(post("/api/admin/events/bulk/preview")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(bulk))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESULT_INQUIRY_FIXED_DATE_BEFORE_EVENT"));
    }

    @Test
    void manualDisableKeepsExistingIssueManageableAndReopenRequiresFutureDeadline() throws Exception {
        Event event = createOpenInquiryEvent("Manageable submission window", "manageable-submission-window");
        importPublishedFixture(
                event.getId(), "manageable-window.csv", inquiryCsv().getBytes(StandardCharsets.UTF_8)
        );
        Registration publicRegistration = registrationRepository
                .findAllByRaceEventIdAndBib(event.getId(), "817").getFirst();
        Result publicResult = resultRepository.findByRegistrationId(publicRegistration.getId()).orElseThrow();
        String correction = """
                {"birthDate":"1990-01-01","correctionReason":"OTHER",
                 "contactEmail":"runner@example.org","message":"Проверьте результат"}
                """;
        MvcResult created = mockMvc.perform(post(
                        "/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), publicResult.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(correction))
                .andExpect(status().isCreated())
                .andReturn();
        long issueId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("issueId").asLong();

        LocalDate futureFixedDate = LocalDate.now(java.time.ZoneId.of(event.getTimeZone())).plusDays(5);
        var fixedMode = eventService.updateResultInquirySettings(
                event.getId(),
                new UpdateResultInquirySettingsRequest(
                        true, ResultInquiryDeadlineMode.FIXED_DATE, 5, futureFixedDate, "timing@example.org"
                ),
                ADMIN_USERNAME
        );
        assertThat(fixedMode.deadlineMode()).isEqualTo(ResultInquiryDeadlineMode.FIXED_DATE);
        assertThat(fixedMode.availability()).isEqualTo(ResultInquiryAvailability.OPEN);
        assertThat(resultIssueRequestRepository.findById(issueId)).isPresent();

        eventService.updateResultInquirySettings(event.getId(), new UpdateResultInquirySettingsRequest(
                false, ResultInquiryDeadlineMode.FIXED_DATE, 5, futureFixedDate, "timing@example.org"
        ), ADMIN_USERNAME);
        mockMvc.perform(post(
                        "/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), publicResult.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(correction))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_ISSUES_DISABLED"));

        mockMvc.perform(put(
                        "/api/admin/events/{eventId}/result-issue-requests/{issueId}/status",
                        event.getId(), issueId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedStatus\":\"NEW\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        assertThat(resultIssueRequestRepository.findById(issueId).orElseThrow().getStatus())
                .isEqualTo(ResultIssueStatus.IN_PROGRESS);

        Event movedIntoPast = eventRepository.findById(event.getId()).orElseThrow();
        movedIntoPast.setStartsAt(Instant.now().minus(Duration.ofDays(11)));
        movedIntoPast.setEndsAt(Instant.now().minus(Duration.ofDays(10)));
        eventRepository.saveAndFlush(movedIntoPast);
        assertThatThrownBy(() -> eventService.updateResultInquirySettings(
                event.getId(), new UpdateResultInquirySettingsRequest(true, 5, "timing@example.org"),
                ADMIN_USERNAME
        )).isInstanceOf(InvalidRequestException.class)
                .extracting(exception -> ((InvalidRequestException) exception).getCode())
                .isEqualTo("RESULT_INQUIRY_DEADLINE_EXPIRED");

        var reopened = eventService.updateResultInquirySettings(
                event.getId(), new UpdateResultInquirySettingsRequest(true, 15, "timing@example.org"),
                ADMIN_USERNAME
        );
        assertThat(reopened.availability()).isEqualTo(ResultInquiryAvailability.OPEN);
        String missing = """
                {"bib":"0817","birthDate":"1991-02-03","contactEmail":"runner@example.org",
                 "message":"Результат отсутствует"}
                """;
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(missing))
                .andExpect(status().isCreated());

        var shortened = eventService.updateResultInquirySettings(
                event.getId(), new UpdateResultInquirySettingsRequest(true, 1, "timing@example.org"),
                ADMIN_USERNAME
        );
        assertThat(shortened.availability()).isEqualTo(ResultInquiryAvailability.CLOSED);
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(missing))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_ISSUES_CLOSED"));
        assertThat(resultIssueRequestRepository.findById(issueId)).isPresent();
    }

    @Test
    void looksUpExactEventBibWithoutPublishingMissingOrInternalResults() throws Exception {
        Event event = createPublishedEvent("Result inquiry lookup", "result-inquiry-lookup");
        event.setStartsAt(Instant.now().minus(Duration.ofDays(2)));
        event.setEndsAt(Instant.now().minus(Duration.ofDays(1)));
        event = eventRepository.saveAndFlush(event);
        Long eventId = event.getId();
        eventService.updateResultInquirySettings(event.getId(), new UpdateResultInquirySettingsRequest(
                true, 5, "timing@example.org"
        ), ADMIN_USERNAME);
        assertThat(importPublishedFixture(
                event.getId(), "result-inquiry.csv", inquiryCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);

        Registration withoutResult = registrationRepository.findAllByRaceEventIdAndBib(
                event.getId(), "8170").getFirst();
        Result removedResult = resultRepository.findByRegistrationId(withoutResult.getId()).orElseThrow();
        resultRepository.delete(removedResult);
        resultRepository.flush();
        long registrationsBefore = registrationRepository.count();
        long resultsBefore = resultRepository.count();

        var publicResult = resultInquiryService.lookup(event.getId(), "817");
        assertThat(publicResult.lookupState()).isEqualTo(ResultInquiryLookupState.RESULT_PUBLIC);
        assertThat(publicResult.inquiryAvailability()).isEqualTo(ResultInquiryAvailability.OPEN);
        assertThat(publicResult.publicResultId()).isNotNull();
        assertThat(publicResult.missingResultActionAvailable()).isFalse();
        assertThat(publicResult.contactEmail()).isNull();

        var notFound = resultInquiryService.lookup(event.getId(), "1817");
        assertThat(notFound.lookupState()).isEqualTo(ResultInquiryLookupState.NOT_FOUND);
        assertThat(notFound.participantDisplayName()).isNull();
        var leadingZero = resultInquiryService.lookup(event.getId(), "0817");
        assertThat(leadingZero.lookupState()).isEqualTo(ResultInquiryLookupState.RESULT_NOT_PUBLIC);
        assertThat(leadingZero.bib()).isEqualTo("0817");
        assertThat(leadingZero.participantDisplayName()).isEqualTo("Скрытый Статус");
        assertThat(leadingZero.raceDisplayName()).isEqualTo("10 km");
        assertThat(leadingZero.startDisplayName()).isEqualTo("10 km");
        assertThat(leadingZero.publicResultId()).isNull();
        assertThat(leadingZero.missingResultActionAvailable()).isTrue();
        assertThat(leadingZero.contactEmail()).isEqualTo("timing@example.org");
        var absentResult = resultInquiryService.lookup(event.getId(), "8170");
        assertThat(absentResult.lookupState()).isEqualTo(ResultInquiryLookupState.RESULT_NOT_PUBLIC);
        assertThat(absentResult.missingResultActionAvailable()).isTrue();

        Race privateStatusRace = raceRepository.findByEventIdAndSourceCode(event.getId(), "10 km").orElseThrow();
        assertThat(resultQueryService.search(
                event.getId(), privateStatusRace.getId(), null, "0817", null, null, null,
                0, 20, "bib", "asc"
        ).content()).isEmpty();
        String response = mockMvc.perform(get("/api/events/{eventId}/result-inquiry", event.getId())
                        .param("bib", "0817"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupState").value("RESULT_NOT_PUBLIC"))
                .andExpect(jsonPath("$.inquiryAvailability").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response)
                .doesNotContainIgnoringCase("quarantine", "birthDate", "sourceCategory", "importBatch", "audit");
        mockMvc.perform(get("/api/events/{eventId}/result-inquiry", event.getId())
                        .param("name", "Скрытый Статус"))
                .andExpect(status().isBadRequest());
        assertThat(registrationRepository.count()).isEqualTo(registrationsBefore);
        assertThat(resultRepository.count()).isEqualTo(resultsBefore);
    }

    @Test
    void resultInquiryFailsClosedAcrossPublicVisibilityBoundaries() {
        Event event = createPublishedEvent("Result inquiry visibility", "result-inquiry-visibility");
        event.setStartsAt(Instant.now().minus(Duration.ofDays(2)));
        event.setEndsAt(Instant.now().minus(Duration.ofDays(1)));
        event = eventRepository.saveAndFlush(event);
        Long eventId = event.getId();
        eventService.updateResultInquirySettings(event.getId(), new UpdateResultInquirySettingsRequest(
                true, 5, "timing@example.org"
        ), ADMIN_USERNAME);
        importPublishedFixture(
                event.getId(), "result-inquiry-visible.csv",
                inquiryVisibilityCsv().getBytes(StandardCharsets.UTF_8)
        );
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        assertThat(resultInquiryService.lookup(event.getId(), "V-1").lookupState())
                .isEqualTo(ResultInquiryLookupState.RESULT_NOT_PUBLIC);
        race.setPublicVisible(false);
        raceRepository.saveAndFlush(race);
        assertThat(resultInquiryService.lookup(event.getId(), "V-1").lookupState())
                .isEqualTo(ResultInquiryLookupState.NOT_FOUND);
        race.setPublicVisible(true);
        raceRepository.saveAndFlush(race);

        event.setResultsPublicationStatus(ru.sportsresults.domain.ResultsPublicationStatus.DRAFT);
        eventRepository.saveAndFlush(event);
        assertThat(resultInquiryService.lookup(eventId, "V-1").lookupState())
                .isEqualTo(ResultInquiryLookupState.RESULT_NOT_PUBLIC);
        event.setResultsPublicationStatus(ru.sportsresults.domain.ResultsPublicationStatus.PUBLISHED);
        event.setPublicationStatus(EventPublicationStatus.DRAFT);
        eventRepository.saveAndFlush(event);
        assertThatThrownBy(() -> resultInquiryService.lookup(eventId, "V-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicateInquiryBibRequiresDobAndDoesNotLeakCandidates() throws Exception {
        Event event = createOpenInquiryEvent("Duplicate inquiry bib", "duplicate-inquiry-bib");
        assertThat(importPublishedFixture(
                event.getId(), "duplicate-inquiry.csv",
                duplicateInquiryBibCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        long auditEntriesBeforeVerification = changeLogRepository.count();

        var response = resultInquiryService.lookup(event.getId(), "1100");
        assertThat(response.lookupState()).isEqualTo(ResultInquiryLookupState.NEEDS_VERIFICATION);
        assertThat(response.participantDisplayName()).isNull();
        assertThat(response.raceId()).isNull();
        assertThat(response.publicResultId()).isNull();
        assertThat(response.missingResultActionAvailable()).isTrue();

        String initialJson = mockMvc.perform(get("/api/events/{eventId}/result-inquiry", event.getId())
                        .param("bib", "1100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupState").value("NEEDS_VERIFICATION"))
                .andExpect(jsonPath("$.missingResultActionAvailable").value(true))
                .andExpect(jsonPath("$.participantDisplayName").doesNotExist())
                .andExpect(jsonPath("$.raceId").doesNotExist())
                .andExpect(jsonPath("$.publicResultId").doesNotExist())
                .andExpect(jsonPath("$.activeIssue").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(initialJson).doesNotContain(
                "Первый Дубль", "Второй Дубль", "1990-01-10", "1991-02-20",
                "birthDate", "candidateCount", "registrationId", "status"
        );

        mockMvc.perform(get("/api/events/{eventId}/result-inquiry", event.getId())
                        .param("bib", "1100")
                        .param("birthDate", "1990-01-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupState").value("NEEDS_VERIFICATION"));

        var nonPublic = resultInquiryService.verify(event.getId(), "1100", LocalDate.parse("1990-01-10"));
        assertThat(nonPublic.lookupState()).isEqualTo(ResultInquiryLookupState.RESULT_NOT_PUBLIC);
        assertThat(nonPublic.participantDisplayName()).isEqualTo("Первый Дубль");
        assertThat(nonPublic.raceDisplayName()).isEqualTo("5 km");
        assertThat(nonPublic.startDisplayName()).isEqualTo("5 km");

        String publicJson = mockMvc.perform(post("/api/events/{eventId}/result-inquiry/verify", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bib\":\"1100\",\"birthDate\":\"1991-02-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupState").value("RESULT_PUBLIC"))
                .andExpect(jsonPath("$.participantDisplayName").value("Второй Дубль"))
                .andReturn().getResponse().getContentAsString();
        assertThat(publicJson).doesNotContain("birthDate", "1991-02-20", "status", "sourceCategory");

        mockMvc.perform(post("/api/events/{eventId}/result-inquiry/verify", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bib\":\"1100\"}"))
                .andExpect(status().isBadRequest());

        var wrongDob = resultInquiryService.verify(event.getId(), "1100", LocalDate.parse("1980-03-15"));
        assertThat(wrongDob.lookupState()).isEqualTo(ResultInquiryLookupState.VERIFICATION_FAILED);
        assertThat(wrongDob.contactEmail()).isEqualTo("timing@example.org");
        assertThat(wrongDob.participantDisplayName()).isNull();
        assertThat(wrongDob.raceId()).isNull();
        assertThat(wrongDob.publicResultId()).isNull();
        assertThat(changeLogRepository.count()).isEqualTo(auditEntriesBeforeVerification);
    }

    @Test
    void successfulDobVerificationReturnsOnlyActiveIssueIdAndClosedHistoryDoesNotBlock() throws Exception {
        Event event = createOpenInquiryEvent("Early active issue check", "early-active-issue-check");
        assertThat(importPublishedFixture(
                event.getId(), "early-active-issue-check.csv",
                duplicateInquiryBibCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        List<Registration> registrations = registrationRepository
                .findAllByRaceEventIdAndBib(event.getId(), "1100");
        Registration missingRegistration = registrations.stream()
                .filter(registration -> LocalDate.parse("1990-01-10").equals(registration.getBirthDate()))
                .findFirst()
                .orElseThrow();
        Registration correctionRegistration = registrations.stream()
                .filter(registration -> LocalDate.parse("1991-02-20").equals(registration.getBirthDate()))
                .findFirst()
                .orElseThrow();

        ResultIssueRequest missingIssue = createAdminIssue(
                event, missingRegistration, ResultIssueType.MISSING_RESULT,
                ResultIssueStatus.NEW, null, 41
        );

        mockMvc.perform(get("/api/events/{eventId}/result-inquiry", event.getId())
                        .param("bib", "1100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupState").value("NEEDS_VERIFICATION"))
                .andExpect(jsonPath("$.activeIssue").doesNotExist());
        mockMvc.perform(post("/api/events/{eventId}/result-inquiry/verify", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bib\":\"1100\",\"birthDate\":\"1980-01-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupState").value("VERIFICATION_FAILED"))
                .andExpect(jsonPath("$.contactEmail").value("timing@example.org"))
                .andExpect(jsonPath("$.activeIssue").doesNotExist());

        String newIssueJson = mockMvc.perform(post(
                        "/api/events/{eventId}/result-inquiry/verify", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bib\":\"1100\",\"birthDate\":\"1990-01-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupState").value("RESULT_NOT_PUBLIC"))
                .andExpect(jsonPath("$.activeIssue.issueId").value(missingIssue.getId()))
                .andReturn().getResponse().getContentAsString();
        assertThat(newIssueJson).doesNotContain(
                "runner41@example.org", "message", "Проверка обращения",
                "attachments", "claimedGunTime", "storageKey", "issueType", "status"
        );

        missingIssue.setStatus(ResultIssueStatus.IN_PROGRESS);
        resultIssueRequestRepository.saveAndFlush(missingIssue);
        mockMvc.perform(post("/api/events/{eventId}/result-inquiry/verify", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bib\":\"1100\",\"birthDate\":\"1990-01-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeIssue.issueId").value(missingIssue.getId()));

        missingIssue.setStatus(ResultIssueStatus.RESOLVED);
        missingIssue.setResolvedAt(Instant.now());
        resultIssueRequestRepository.saveAndFlush(missingIssue);
        mockMvc.perform(post("/api/events/{eventId}/result-inquiry/verify", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bib\":\"1100\",\"birthDate\":\"1990-01-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeIssue").doesNotExist());

        missingIssue.setStatus(ResultIssueStatus.REJECTED);
        resultIssueRequestRepository.saveAndFlush(missingIssue);
        mockMvc.perform(post("/api/events/{eventId}/result-inquiry/verify", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bib\":\"1100\",\"birthDate\":\"1990-01-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeIssue").doesNotExist());

        ResultIssueRequest correctionIssueOnMissingResult = createAdminIssue(
                event, missingRegistration, ResultIssueType.RESULT_CORRECTION,
                ResultIssueStatus.NEW, ResultCorrectionReason.OTHER, 42
        );
        mockMvc.perform(post("/api/events/{eventId}/result-inquiry/verify", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bib\":\"1100\",\"birthDate\":\"1990-01-10\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupState").value("RESULT_NOT_PUBLIC"))
                .andExpect(jsonPath("$.activeIssue.issueId").value(correctionIssueOnMissingResult.getId()));

        ResultIssueRequest missingIssueOnPublicResult = createAdminIssue(
                event, correctionRegistration, ResultIssueType.MISSING_RESULT,
                ResultIssueStatus.NEW, null, 43
        );
        mockMvc.perform(post("/api/events/{eventId}/result-inquiry/verify", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bib\":\"1100\",\"birthDate\":\"1991-02-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupState").value("RESULT_PUBLIC"))
                .andExpect(jsonPath("$.activeIssue.issueId").value(missingIssueOnPublicResult.getId()));

        correctionIssueOnMissingResult.setStatus(ResultIssueStatus.RESOLVED);
        correctionIssueOnMissingResult.setResolvedAt(Instant.now());
        resultIssueRequestRepository.saveAndFlush(correctionIssueOnMissingResult);
        missingIssueOnPublicResult.setStatus(ResultIssueStatus.RESOLVED);
        missingIssueOnPublicResult.setResolvedAt(Instant.now());
        resultIssueRequestRepository.saveAndFlush(missingIssueOnPublicResult);
        ResultIssueRequest correctionIssue = createAdminIssue(
                event, correctionRegistration, ResultIssueType.RESULT_CORRECTION,
                ResultIssueStatus.NEW, ResultCorrectionReason.OTHER, 44
        );
        mockMvc.perform(post("/api/events/{eventId}/result-inquiry/verify", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bib\":\"1100\",\"birthDate\":\"1991-02-20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupState").value("RESULT_PUBLIC"))
                .andExpect(jsonPath("$.activeIssue.issueId").value(correctionIssue.getId()));

        Event uniqueEvent = createOpenInquiryEvent("Unique active issue privacy", "unique-active-issue-privacy");
        importPublishedFixture(
                uniqueEvent.getId(), "unique-active-issue-privacy.csv",
                singleInquiryBibCsv("Один", "finished").getBytes(StandardCharsets.UTF_8)
        );
        Registration uniqueRegistration = registrationRepository
                .findAllByRaceEventIdAndBib(uniqueEvent.getId(), "1100").getFirst();
        ResultIssueRequest uniqueIssue = createAdminIssue(
                uniqueEvent, uniqueRegistration, ResultIssueType.RESULT_CORRECTION,
                ResultIssueStatus.NEW, ResultCorrectionReason.OTHER, 45
        );
        mockMvc.perform(get("/api/events/{eventId}/result-inquiry", uniqueEvent.getId())
                        .param("bib", "1100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lookupState").value("RESULT_PUBLIC"))
                .andExpect(jsonPath("$.activeIssue").doesNotExist());
        assertThat(uniqueIssue.getId()).isPositive();
    }

    @Test
    void duplicateInquirySeparatesAllPublicAllNonPublicAndArbitraryCandidateCounts() {
        Event allPublic = createOpenInquiryEvent("All public duplicate", "all-public-duplicate");
        importPublishedFixture(
                allPublic.getId(), "all-public-duplicate.csv",
                allPublicDuplicateBibCsv().getBytes(StandardCharsets.UTF_8)
        );
        Race allPublicRace = raceRepository.findByEventIdAndSourceCode(allPublic.getId(), "5 km").orElseThrow();
        assertThat(resultQueryService.search(
                allPublic.getId(), allPublicRace.getId(), null, "4400", null, null, null,
                0, 20, "bib", "asc"
        ).content()).hasSize(2);
        var allPublicLookup = resultInquiryService.lookup(allPublic.getId(), "4400");
        assertThat(allPublicLookup.lookupState()).isEqualTo(ResultInquiryLookupState.NEEDS_VERIFICATION);
        assertThat(allPublicLookup.missingResultActionAvailable()).isFalse();

        Event allNonPublic = createOpenInquiryEvent("All non-public duplicate", "all-non-public-duplicate");
        importPublishedFixture(
                allNonPublic.getId(), "all-non-public-duplicate.csv",
                allNonPublicDuplicateBibCsv().getBytes(StandardCharsets.UTF_8)
        );
        Race allNonPublicRace = raceRepository.findByEventIdAndSourceCode(
                allNonPublic.getId(), "5 km"
        ).orElseThrow();
        assertThat(resultQueryService.search(
                allNonPublic.getId(), allNonPublicRace.getId(), null, "5500", null, null, null,
                0, 20, "bib", "asc"
        ).content()).isEmpty();
        var allNonPublicLookup = resultInquiryService.lookup(allNonPublic.getId(), "5500");
        assertThat(allNonPublicLookup.lookupState()).isEqualTo(ResultInquiryLookupState.NEEDS_VERIFICATION);
        assertThat(allNonPublicLookup.missingResultActionAvailable()).isTrue();

        Event threeCandidates = createOpenInquiryEvent("Three duplicate candidates", "three-duplicate-candidates");
        importPublishedFixture(
                threeCandidates.getId(), "three-duplicate-candidates.csv",
                threeCandidateBibCsv().getBytes(StandardCharsets.UTF_8)
        );
        Race threeCandidateRace = raceRepository.findByEventIdAndSourceCode(
                threeCandidates.getId(), "5 km"
        ).orElseThrow();
        assertThat(resultQueryService.search(
                threeCandidates.getId(), threeCandidateRace.getId(), null, "6600", null, null, null,
                0, 20, "bib", "asc"
        ).content()).singleElement().satisfies(item -> assertThat(item.displayName()).isEqualTo("Первый Три"));
        var threeLookup = resultInquiryService.lookup(threeCandidates.getId(), "6600");
        assertThat(threeLookup.lookupState()).isEqualTo(ResultInquiryLookupState.NEEDS_VERIFICATION);
        assertThat(threeLookup.missingResultActionAvailable()).isTrue();
        assertThat(resultInquiryService.verify(
                threeCandidates.getId(), "6600", LocalDate.parse("1990-01-10")
        ).lookupState()).isEqualTo(ResultInquiryLookupState.RESULT_PUBLIC);
        assertThat(resultInquiryService.verify(
                threeCandidates.getId(), "6600", LocalDate.parse("1991-02-20")
        )).satisfies(verified -> {
            assertThat(verified.lookupState()).isEqualTo(ResultInquiryLookupState.RESULT_NOT_PUBLIC);
            assertThat(verified.participantDisplayName()).isEqualTo("Второй Три");
        });
        assertThat(resultInquiryService.verify(
                threeCandidates.getId(), "6600", LocalDate.parse("1992-03-30")
        )).satisfies(verified -> {
            assertThat(verified.lookupState()).isEqualTo(ResultInquiryLookupState.RESULT_NOT_PUBLIC);
            assertThat(verified.participantDisplayName()).isEqualTo("Третий Три");
        });

        Event ambiguousThree = createOpenInquiryEvent("Ambiguous three candidates", "ambiguous-three-candidates");
        importPublishedFixture(
                ambiguousThree.getId(), "ambiguous-three-candidates.csv",
                ambiguousThreeCandidateBibCsv().getBytes(StandardCharsets.UTF_8)
        );
        var ambiguous = resultInquiryService.verify(
                ambiguousThree.getId(), "7700", LocalDate.parse("1991-02-20")
        );
        assertThat(ambiguous.lookupState()).isEqualTo(ResultInquiryLookupState.VERIFICATION_FAILED);
        assertThat(ambiguous.participantDisplayName()).isNull();
        assertThat(ambiguous.missingResultActionAvailable()).isTrue();
    }

    @Test
    void dobVerificationNeverUsesAnImplicitTieBreakerOrNullDob() {
        Event sameDobEvent = createOpenInquiryEvent("Same DOB inquiry", "same-dob-inquiry");
        importPublishedFixture(
                sameDobEvent.getId(), "same-dob-inquiry.csv",
                sameDobInquiryBibCsv().getBytes(StandardCharsets.UTF_8)
        );
        var unresolved = resultInquiryService.verify(
                sameDobEvent.getId(), "2200", LocalDate.parse("1990-01-10")
        );
        assertThat(unresolved.lookupState()).isEqualTo(ResultInquiryLookupState.VERIFICATION_FAILED);
        assertThat(unresolved.participantDisplayName()).isNull();

        Event nullDobEvent = createOpenInquiryEvent("Null DOB inquiry", "null-dob-inquiry");
        importPublishedFixture(
                nullDobEvent.getId(), "null-dob-inquiry.csv",
                nullDobInquiryBibCsv().getBytes(StandardCharsets.UTF_8)
        );
        assertThat(resultInquiryService.lookup(nullDobEvent.getId(), "3300").lookupState())
                .isEqualTo(ResultInquiryLookupState.NEEDS_VERIFICATION);
        assertThat(resultInquiryService.verify(
                nullDobEvent.getId(), "3300", LocalDate.parse("1980-03-15")
        ).lookupState()).isEqualTo(ResultInquiryLookupState.VERIFICATION_FAILED);
        assertThat(resultInquiryService.verify(
                nullDobEvent.getId(), "3300", LocalDate.parse("1992-04-12")
        ).lookupState()).isEqualTo(ResultInquiryLookupState.RESULT_PUBLIC);
    }

    @Test
    void dobVerificationDoesNotBypassRaceVisibility() {
        Event event = createOpenInquiryEvent("Hidden duplicate inquiry", "hidden-duplicate-inquiry");
        importPublishedFixture(
                event.getId(), "hidden-duplicate-inquiry.csv",
                duplicateInquiryBibCsv().getBytes(StandardCharsets.UTF_8)
        );
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        race.setPublicVisible(false);
        raceRepository.saveAndFlush(race);
        assertThat(resultInquiryService.lookup(event.getId(), "1100").lookupState())
                .isEqualTo(ResultInquiryLookupState.NEEDS_VERIFICATION);
        var hiddenRace = resultInquiryService.verify(event.getId(), "1100", LocalDate.parse("1990-01-10"));
        assertThat(hiddenRace.lookupState()).isEqualTo(ResultInquiryLookupState.VERIFICATION_FAILED);
        assertThat(hiddenRace.participantDisplayName()).isNull();

    }

    @Test
    void duplicateBibDetectionIsScopedToOneEvent() {
        Event eventA = createOpenInquiryEvent("Inquiry event A", "inquiry-event-a");
        Event eventB = createOpenInquiryEvent("Inquiry event B", "inquiry-event-b");
        importPublishedFixture(
                eventA.getId(), "event-a-inquiry.csv",
                singleInquiryBibCsv("Первый", "notstarted").getBytes(StandardCharsets.UTF_8)
        );
        importPublishedFixture(
                eventB.getId(), "event-b-inquiry.csv",
                singleInquiryBibCsv("Второй", "finished").getBytes(StandardCharsets.UTF_8)
        );

        assertThat(resultInquiryService.lookup(eventA.getId(), "1100").lookupState())
                .isEqualTo(ResultInquiryLookupState.RESULT_NOT_PUBLIC);
        assertThat(resultInquiryService.lookup(eventB.getId(), "1100").lookupState())
                .isEqualTo(ResultInquiryLookupState.RESULT_PUBLIC);
    }

    @Test
    void failedDobFallbackUsesOnlyTheEventEmailAndCannotBypassAvailability() {
        Event afterDays = createOpenInquiryEvent("After-days fallback", "after-days-fallback");
        eventService.updateResultInquirySettings(afterDays.getId(), new UpdateResultInquirySettingsRequest(
                true, ResultInquiryDeadlineMode.AFTER_EVENT_DAYS, 5, null, "event-a@example.com"
        ), ADMIN_USERNAME);
        importPublishedFixture(
                afterDays.getId(), "after-days-fallback.csv",
                singleInquiryBibCsv("After", "finished").getBytes(StandardCharsets.UTF_8)
        );
        var afterDaysFailure = resultInquiryService.verify(
                afterDays.getId(), "1100", LocalDate.parse("1980-01-01")
        );
        assertThat(afterDaysFailure.lookupState()).isEqualTo(ResultInquiryLookupState.VERIFICATION_FAILED);
        assertThat(afterDaysFailure.inquiryAvailability()).isEqualTo(ResultInquiryAvailability.OPEN);
        assertThat(afterDaysFailure.contactEmail()).isEqualTo("event-a@example.com");

        Event fixedDate = createOpenInquiryEvent("Fixed-date fallback", "fixed-date-fallback");
        LocalDate fixedDeadline = LocalDate.now(java.time.ZoneId.of(fixedDate.getTimeZone())).plusDays(2);
        eventService.updateResultInquirySettings(fixedDate.getId(), new UpdateResultInquirySettingsRequest(
                true, ResultInquiryDeadlineMode.FIXED_DATE, null, fixedDeadline, "event-b@example.com"
        ), ADMIN_USERNAME);
        importPublishedFixture(
                fixedDate.getId(), "fixed-date-fallback.csv",
                singleInquiryBibCsv("Fixed", "finished").getBytes(StandardCharsets.UTF_8)
        );
        var fixedDateFailure = resultInquiryService.verify(
                fixedDate.getId(), "1100", LocalDate.parse("1980-01-01")
        );
        assertThat(fixedDateFailure.lookupState()).isEqualTo(ResultInquiryLookupState.VERIFICATION_FAILED);
        assertThat(fixedDateFailure.inquiryAvailability()).isEqualTo(ResultInquiryAvailability.OPEN);
        assertThat(fixedDateFailure.contactEmail()).isEqualTo("event-b@example.com");

        eventService.updateResultInquirySettings(afterDays.getId(), new UpdateResultInquirySettingsRequest(
                false, ResultInquiryDeadlineMode.AFTER_EVENT_DAYS, 5, null, "event-a@example.com"
        ), ADMIN_USERNAME);
        var disabledFailure = resultInquiryService.verify(
                afterDays.getId(), "1100", LocalDate.parse("1980-01-01")
        );
        assertThat(disabledFailure.inquiryAvailability()).isEqualTo(ResultInquiryAvailability.DISABLED);
        assertThat(disabledFailure.contactEmail()).isNull();

        eventService.updateResultInquirySettings(fixedDate.getId(), new UpdateResultInquirySettingsRequest(
                true,
                ResultInquiryDeadlineMode.FIXED_DATE,
                null,
                LocalDate.now(java.time.ZoneId.of(fixedDate.getTimeZone())).minusDays(1),
                "event-b@example.com"
        ), ADMIN_USERNAME);
        var closedFailure = resultInquiryService.verify(
                fixedDate.getId(), "1100", LocalDate.parse("1980-01-01")
        );
        assertThat(closedFailure.inquiryAvailability()).isEqualTo(ResultInquiryAvailability.CLOSED);
        assertThat(closedFailure.contactEmail()).isNull();

        Event notOpen = createPublishedEvent("Not-open fallback", "not-open-fallback");
        notOpen.setStartsAt(Instant.now().plus(Duration.ofDays(2)));
        notOpen.setEndsAt(Instant.now().plus(Duration.ofDays(2)).plus(Duration.ofHours(2)));
        notOpen = eventRepository.saveAndFlush(notOpen);
        eventService.updateResultInquirySettings(notOpen.getId(), new UpdateResultInquirySettingsRequest(
                true,
                ResultInquiryDeadlineMode.FIXED_DATE,
                null,
                LocalDate.now(java.time.ZoneId.of(notOpen.getTimeZone())).plusDays(4),
                "not-open@example.com"
        ), ADMIN_USERNAME);
        importPublishedFixture(
                notOpen.getId(), "not-open-fallback.csv",
                singleInquiryBibCsv("Future", "finished").getBytes(StandardCharsets.UTF_8)
        );
        var notOpenFailure = resultInquiryService.verify(
                notOpen.getId(), "1100", LocalDate.parse("1980-01-01")
        );
        assertThat(notOpenFailure.inquiryAvailability()).isEqualTo(ResultInquiryAvailability.NOT_OPEN_YET);
        assertThat(notOpenFailure.contactEmail()).isNull();
    }

    @Test
    void createsMissingResultIssuesWithoutTrustingRegistrationIdAndAllowsHistory() throws Exception {
        Event event = createOpenInquiryEvent("Missing result issues", "missing-result-issues");
        assertThat(importPublishedFixture(
                event.getId(), "missing-result-issues.csv", inquiryCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);

        Registration expected = registrationRepository.findAllByRaceEventIdAndBib(event.getId(), "0817").getFirst();
        Registration arbitrary = registrationRepository.findAllByRaceEventIdAndBib(event.getId(), "817").getFirst();
        Result linkedNonPublicResult = resultRepository.findByRegistrationId(expected.getId()).orElseThrow();
        String originalStatus = linkedNonPublicResult.getStatus();
        Duration originalGunTime = linkedNonPublicResult.getGunTime();
        Duration originalChipTime = linkedNonPublicResult.getChipTime();

        String request = """
                {
                  "bib": "0817",
                  "birthDate": "1991-02-03",
                  "registrationId": %d,
                  "contactEmail": "runner@example.org",
                  "message": "Не вижу результат",
                  "estimatedStartAt": "2026-08-01T06:00:00Z",
                  "estimatedFinishAt": "2026-08-01T07:30:00Z"
                }
                """.formatted(arbitrary.getId());

        String response = mockMvc.perform(post(
                        "/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("MISSING_RESULT"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.issueId").isNumber())
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(
                "birthDate", "1991-02-03", "registrationId", "runner@example.org",
                "quarantine", "ImportBatch"
        );

        ResultIssueRequest created = resultIssueRequestRepository.findAll().getFirst();
        assertThat(created.getEvent().getId()).isEqualTo(event.getId());
        assertThat(created.getRegistration().getId()).isEqualTo(expected.getId());
        assertThat(created.getRegistration().getId()).isNotEqualTo(arbitrary.getId());
        assertThat(created.getResult().getId()).isEqualTo(linkedNonPublicResult.getId());
        assertThat(created.getIssueType()).isEqualTo(ResultIssueType.MISSING_RESULT);
        assertThat(created.getStatus()).isEqualTo(ResultIssueStatus.NEW);
        assertThat(created.getCorrectionReason()).isNull();
        assertThat(created.getEstimatedStartAt()).isEqualTo(Instant.parse("2026-08-01T06:00:00Z"));
        assertThat(created.getEstimatedFinishAt()).isEqualTo(Instant.parse("2026-08-01T07:30:00Z"));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='result_issue_requests'
                  AND column_name='birth_date'
                """, Integer.class)).isZero();

        Result unchanged = resultRepository.findById(linkedNonPublicResult.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(originalStatus);
        assertThat(unchanged.getGunTime()).isEqualTo(originalGunTime);
        assertThat(unchanged.getChipTime()).isEqualTo(originalChipTime);

        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RESULT_ISSUE_EXISTS"));

        long issueCount = resultIssueRequestRepository.count();
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("1991-02-03", "1981-02-03")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));
        assertThat(resultIssueRequestRepository.count()).isEqualTo(issueCount);

        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("0817", "817").replace("1991-02-03", "1990-01-01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PUBLIC_RESULT_EXISTS"));
        assertThat(resultIssueRequestRepository.count()).isEqualTo(issueCount);

        created.setStatus(ResultIssueStatus.RESOLVED);
        created.setResolvedAt(Instant.now());
        resultIssueRequestRepository.saveAndFlush(created);
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());
        assertThat(resultIssueRequestRepository.count()).isEqualTo(2);

        Registration registrationWithoutResult = registrationRepository
                .findAllByRaceEventIdAndBib(event.getId(), "8170").getFirst();
        resultRepository.delete(resultRepository.findByRegistrationId(registrationWithoutResult.getId()).orElseThrow());
        resultRepository.flush();
        String absentResultRequest = request
                .replace("\"0817\"", "\"8170\"")
                .replace("1991-02-03", "1992-03-04");
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(absentResultRequest))
                .andExpect(status().isCreated());
        ResultIssueRequest withoutResultLink = resultIssueRequestRepository.findAll().stream()
                .filter(candidate -> candidate.getRegistration().getId().equals(registrationWithoutResult.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(withoutResultLink.getResult()).isNull();
    }

    @Test
    void missingResultIssueResolvesDuplicateBibOnlyByDob() throws Exception {
        Event event = createOpenInquiryEvent("Duplicate missing issues", "duplicate-missing-issues");
        importPublishedFixture(
                event.getId(), "duplicate-missing-issues.csv",
                duplicateInquiryBibCsv().getBytes(StandardCharsets.UTF_8)
        );
        String firstCandidate = """
                {"bib":"1100","birthDate":"1990-01-10","contactEmail":"runner@example.org",
                 "message":"Результат отсутствует"}
                """;
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(firstCandidate))
                .andExpect(status().isCreated());
        Long selectedRegistrationId = resultIssueRequestRepository.findAll().getFirst().getRegistration().getId();
        assertThat(registrationRepository.findById(selectedRegistrationId).orElseThrow().getDisplayName())
                .isEqualTo("Первый Дубль");

        long createdCount = resultIssueRequestRepository.count();
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstCandidate.replace("1990-01-10", "1980-01-10")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstCandidate.replace("1990-01-10", "1991-02-20")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PUBLIC_RESULT_EXISTS"));
        assertThat(resultIssueRequestRepository.count()).isEqualTo(createdCount);

        Event ambiguous = createOpenInquiryEvent("Ambiguous missing issues", "ambiguous-missing-issues");
        importPublishedFixture(
                ambiguous.getId(), "ambiguous-missing-issues.csv",
                sameDobInquiryBibCsv().getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", ambiguous.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstCandidate.replace("1100", "2200")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));
        assertThat(resultIssueRequestRepository.count()).isEqualTo(createdCount);
    }

    @Test
    void resultIssueCreationUsesSharedInquiryWindow() throws Exception {
        Event disabled = createPublishedEvent("Disabled issues", "disabled-issues");
        disabled.setStartsAt(Instant.now().minus(Duration.ofDays(2)));
        disabled.setEndsAt(Instant.now().minus(Duration.ofDays(1)));
        disabled = eventRepository.saveAndFlush(disabled);
        importPublishedFixture(
                disabled.getId(), "disabled-issues.csv",
                singleInquiryBibCsv("Отключённый", "notstarted").getBytes(StandardCharsets.UTF_8)
        );
        String request = """
                {"bib":"1100","birthDate":"1990-01-10","contactEmail":"runner@example.org",
                 "message":"Результат отсутствует"}
                """;
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", disabled.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_ISSUES_DISABLED"));

        Event expired = createPublishedEvent("Expired issues", "expired-issues");
        expired.setStartsAt(Instant.now().minus(Duration.ofDays(5)));
        expired.setEndsAt(Instant.now().minus(Duration.ofDays(4)));
        expired = eventRepository.saveAndFlush(expired);
        expired.setResultInquiryEnabled(true);
        expired.setResultInquiryWindowDays(1);
        expired.setResultInquiryEmail("timing@example.org");
        eventRepository.saveAndFlush(expired);
        importPublishedFixture(
                expired.getId(), "expired-issues.csv",
                singleInquiryBibCsv("Просроченный", "notstarted").getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", expired.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_ISSUES_CLOSED"));
        assertThat(resultIssueRequestRepository.count()).isZero();
    }

    @Test
    void createsCorrectionIssueWithSnapshotWithoutMutatingResult() throws Exception {
        Event event = createOpenInquiryEvent("Correction issues", "correction-issues");
        importPublishedFixture(
                event.getId(), "correction-issues.csv", inquiryCsv().getBytes(StandardCharsets.UTF_8)
        );
        Registration registration = registrationRepository.findAllByRaceEventIdAndBib(event.getId(), "817").getFirst();
        awardPolicyService.upsert(registration.getRace().getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 3, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();
        String originalStatus = result.getStatus();
        Duration originalGunTime = result.getGunTime();
        Duration originalChipTime = result.getChipTime();
        String request = """
                {"birthDate":"1990-01-01","correctionReason":"OFFICIAL_TIME",
                 "claimedGunTimeMs":1250,"contactEmail":"runner@example.org",
                 "message":"Официальное время указано неверно"}
                """;

        String response = mockMvc.perform(post(
                        "/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), result.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RESULT_CORRECTION"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(
                "birthDate", "1990-01-01", "runner@example.org", "registrationId",
                "observedResultStatus", "finished"
        );

        ResultIssueRequest issue = resultIssueRequestRepository.findAll().getFirst();
        assertThat(issue.getEvent().getId()).isEqualTo(event.getId());
        assertThat(issue.getRegistration().getId()).isEqualTo(registration.getId());
        assertThat(issue.getResult().getId()).isEqualTo(result.getId());
        assertThat(issue.getCorrectionReason()).isEqualTo(ResultCorrectionReason.OFFICIAL_TIME);
        assertThat(issue.getClaimedGunTime()).isEqualTo(Duration.ofMillis(1250));
        assertThat(issue.getClaimedChipTime()).isNull();
        assertThat(issue.getObservedGunTime()).isEqualTo(originalGunTime);
        assertThat(issue.getObservedChipTime()).isEqualTo(originalChipTime);
        assertThat(issue.getObservedResultStatus()).isEqualTo(originalStatus);
        assertThat(issue.getSnapshotOrigin()).isEqualTo(ResultIssueSnapshotOrigin.CAPTURED_AT_CREATION);
        assertThat(issue.getSnapshotEventName()).isEqualTo(event.getName());
        assertThat(issue.getSnapshotRaceId()).isEqualTo(registration.getRace().getId());
        assertThat(issue.getSnapshotBib()).isEqualTo(registration.getBib());
        assertThat(issue.getSnapshotDisplayName()).isEqualTo(registration.getDisplayName());
        assertThat(issue.getSnapshotRanking()).contains("ABSOLUTE");
        assertThat(issue.getSnapshotImportBatchId()).isEqualTo(registration.getImportBatch().getId());
        assertThat(resultIssueHistoryRepository.findAllByIssueRequest_IdOrderByCreatedAtAscIdAsc(issue.getId()))
                .extracting(history -> history.getAction().name())
                .containsExactly("CREATED");

        Result unchanged = resultRepository.findById(result.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(originalStatus);
        assertThat(unchanged.getGunTime()).isEqualTo(originalGunTime);
        assertThat(unchanged.getChipTime()).isEqualTo(originalChipTime);

        long issueCount = resultIssueRequestRepository.count();
        mockMvc.perform(post("/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), result.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("1990-01-01", "1980-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));
        Registration nonPublicRegistration = registrationRepository
                .findAllByRaceEventIdAndBib(event.getId(), "0817").getFirst();
        Result nonPublicResult = resultRepository.findByRegistrationId(nonPublicRegistration.getId()).orElseThrow();
        mockMvc.perform(post("/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), nonPublicResult.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("1990-01-01", "1991-02-03")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDENTITY_VERIFICATION_FAILED"));
        assertThat(resultIssueRequestRepository.count()).isEqualTo(issueCount);

        mockMvc.perform(post("/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), result.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RESULT_ISSUE_EXISTS"));
        mockMvc.perform(post("/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), result.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("OFFICIAL_TIME", "CHIP_TIME")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RESULT_ISSUE_EXISTS"));
        mockMvc.perform(post("/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), result.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("runner@example.org", "another@example.org")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RESULT_ISSUE_EXISTS"));
        mockMvc.perform(post("/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), result.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.replace("Официальное время указано неверно", "Другое сообщение")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RESULT_ISSUE_EXISTS"));
        assertThat(resultIssueRequestRepository.count()).isEqualTo(issueCount);

        issue.setStatus(ResultIssueStatus.REJECTED);
        issue.setResolvedAt(Instant.now());
        resultIssueRequestRepository.saveAndFlush(issue);
        mockMvc.perform(post("/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), result.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated());
        assertThat(resultIssueRequestRepository.count()).isEqualTo(2);
    }

    @Test
    void activeMissingIssueBlocksAnotherTypeForTheSameRegistration() throws Exception {
        Event event = createOpenInquiryEvent("Cross type issues", "cross-type-issues");
        importPublishedFixture(
                event.getId(), "cross-type-issues.csv",
                singleInquiryBibCsv("Один", "notstarted").getBytes(StandardCharsets.UTF_8)
        );
        Registration registration = registrationRepository
                .findAllByRaceEventIdAndBib(event.getId(), "1100").getFirst();
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();
        String missingRequest = """
                {"bib":"1100","birthDate":"1990-01-10","contactEmail":"runner@example.org",
                 "message":"Результат отсутствует"}
                """;
        String correctionRequest = """
                {"birthDate":"1990-01-10","correctionReason":"OTHER",
                 "contactEmail":"another@example.org","message":"Другая проблема"}
                """;

        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(missingRequest))
                .andExpect(status().isCreated());
        result.setStatus("finished");
        resultRepository.saveAndFlush(result);

        mockMvc.perform(post("/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), result.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(correctionRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RESULT_ISSUE_EXISTS"));
        ResultIssueRequest active = resultIssueRequestRepository.findAll().getFirst();
        assertThat(active.getIssueType()).isEqualTo(ResultIssueType.MISSING_RESULT);
        assertThat(resultIssueRequestRepository.count()).isOne();

        active.setStatus(ResultIssueStatus.IN_PROGRESS);
        resultIssueRequestRepository.saveAndFlush(active);
        mockMvc.perform(post("/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), result.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(correctionRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_RESULT_ISSUE_EXISTS"));
        assertThat(resultIssueRequestRepository.count()).isOne();
    }

    @Test
    void differentRegistrationsWithTheSameBibCanEachHaveAnActiveIssue() throws Exception {
        Event event = createOpenInquiryEvent("Same bib separate issues", "same-bib-separate-issues");
        importPublishedFixture(
                event.getId(), "same-bib-separate-issues.csv",
                duplicateInquiryBibCsv().getBytes(StandardCharsets.UTF_8)
        );
        String missingRequest = """
                {"bib":"1100","birthDate":"1990-01-10","contactEmail":"first@example.org",
                 "message":"Нет результата"}
                """;
        mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(missingRequest))
                .andExpect(status().isCreated());

        Registration secondRegistration = registrationRepository.findAllByRaceEventIdAndBib(event.getId(), "1100")
                .stream()
                .filter(registration -> LocalDate.parse("1991-02-20").equals(registration.getBirthDate()))
                .findFirst()
                .orElseThrow();
        Result secondResult = resultRepository.findByRegistrationId(secondRegistration.getId()).orElseThrow();
        String correctionRequest = """
                {"birthDate":"1991-02-20","correctionReason":"OTHER",
                 "contactEmail":"second@example.org","message":"Неверный результат"}
                """;
        mockMvc.perform(post("/api/events/{eventId}/results/{resultId}/result-issue-requests",
                        event.getId(), secondResult.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(correctionRequest))
                .andExpect(status().isCreated());

        assertThat(resultIssueRequestRepository.count()).isEqualTo(2);
        assertThat(resultIssueRequestRepository.findAll())
                .extracting(issue -> issue.getRegistration().getId())
                .doesNotHaveDuplicates();
    }

    @Test
    void concurrentSameTypeHttpRequestsCreateOneIssueAndReturnOneConflict() throws Exception {
        Event event = createOpenInquiryEvent("Concurrent HTTP issues", "concurrent-http-issues");
        importPublishedFixture(
                event.getId(), "concurrent-http-issues.csv",
                singleInquiryBibCsv("Параллельный", "notstarted").getBytes(StandardCharsets.UTF_8)
        );
        String request = """
                {"bib":"1100","birthDate":"1990-01-10","contactEmail":"runner@example.org",
                 "message":"Результат отсутствует"}
                """;
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> call = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent requests did not start in time");
            }
            return mockMvc.perform(post("/api/events/{eventId}/result-issue-requests/missing", event.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(request))
                    .andReturn().getResponse().getStatus();
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(call);
            var second = executor.submit(call);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(resultIssueRequestRepository.count()).isOne();
    }

    @Test
    void concurrentDifferentTypesAreLimitedByTheDatabaseToOneActiveIssue() throws Exception {
        Event event = createOpenInquiryEvent("Concurrent type issues", "concurrent-type-issues");
        importPublishedFixture(
                event.getId(), "concurrent-type-issues.csv",
                singleInquiryBibCsv("Параллельный", "finished").getBytes(StandardCharsets.UTF_8)
        );
        Registration registration = registrationRepository
                .findAllByRaceEventIdAndBib(event.getId(), "1100").getFirst();
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> missingInsert = concurrentIssueInsert(
                ready, start, event.getId(), registration.getId(), null,
                "MISSING_RESULT", null, null
        );
        Callable<Boolean> correctionInsert = concurrentIssueInsert(
                ready, start, event.getId(), registration.getId(), result.getId(),
                "RESULT_CORRECTION", "OTHER", result.getStatus()
        );

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(missingInsert);
            var second = executor.submit(correctionInsert);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM result_issue_requests
                WHERE registration_id=? AND status IN ('NEW', 'IN_PROGRESS')
                """, Long.class, registration.getId())).isOne();
    }

    @Test
    void publicAttachmentCapabilitiesDoNotAdvertiseTheNonUploadingMemoryAdapter() throws Exception {
        mockMvc.perform(get("/api/result-issue-attachments/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directUploadAvailable").value(false))
                .andExpect(jsonPath("$.maxFileSizeBytes").value(10_737_418_240L))
                .andExpect(jsonPath("$.maxAttachmentsPerIssue").value(10));
    }

    @Test
    void attachmentMetadataUsesIssueCapabilityAndBackendGeneratedStorageKeys() throws Exception {
        Event firstEvent = createOpenInquiryEvent("Attachment issue one", "attachment-issue-one");
        importPublishedFixture(
                firstEvent.getId(), "attachment-one.csv",
                singleInquiryBibCsv("Первый", "notstarted").getBytes(StandardCharsets.UTF_8)
        );
        CreatedIssueAccess firstIssue = createMissingIssueAccess(firstEvent.getId(), "1100", "1990-01-10");
        ResultIssueRequest storedIssue = resultIssueRequestRepository.findById(firstIssue.issueId()).orElseThrow();
        assertThat(storedIssue.getAttachmentUploadTokenHash())
                .hasSize(64)
                .isNotEqualTo(firstIssue.token());
        assertThat(storedIssue.getAttachmentUploadTokenExpiresAt()).isAfter(Instant.now());

        long largeSize = 650_000_000L;
        String attachmentRequest = """
                {"originalFileName":"../../evidence.mp4","contentType":"video/mp4",
                 "sizeBytes":%d,"storageKey":"attacker-selected-key"}
                """.formatted(largeSize);
        String firstAttachmentResponse = mockMvc.perform(post(
                        "/api/result-issues/{issueId}/attachments", firstIssue.issueId())
                        .header("X-Result-Issue-Upload-Token", firstIssue.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachmentRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadStatus").value("PENDING_UPLOAD"))
                .andExpect(jsonPath("$.scanStatus").value("PENDING"))
                .andExpect(jsonPath("$.uploadMethod").value("PUT"))
                .andExpect(jsonPath("$.uploadUrl").value(org.hamcrest.Matchers.startsWith("https://")))
                .andReturn().getResponse().getContentAsString();
        assertThat(firstAttachmentResponse).doesNotContain(
                "attacker-selected-key", "../../evidence.mp4", firstIssue.token()
        );

        ResultIssueAttachment firstAttachment = resultIssueAttachmentRepository.findAll().getFirst();
        assertThat(firstAttachment.getIssueRequest().getId()).isEqualTo(firstIssue.issueId());
        assertThat(firstAttachment.getOriginalFileName()).isEqualTo("../../evidence.mp4");
        assertThat(firstAttachment.getStorageKey())
                .startsWith("result-issues/" + firstEvent.getId() + "/" + firstIssue.issueId() + "/")
                .doesNotContain("evidence.mp4", "..", "attacker-selected-key");
        assertThat(firstAttachment.getSizeBytes()).isEqualTo(largeSize);
        assertThat(firstAttachment.getUploadStatus()).isEqualTo(AttachmentUploadStatus.PENDING_UPLOAD);
        assertThat(firstAttachment.getScanStatus()).isEqualTo(AttachmentScanStatus.PENDING);

        long attachmentCount = resultIssueAttachmentRepository.count();
        mockMvc.perform(post("/api/result-issues/{issueId}/attachments", firstIssue.issueId())
                        .header("X-Result-Issue-Upload-Token", "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON).content(attachmentRequest))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESULT_ISSUE_ATTACHMENT_ACCESS_DENIED"));
        mockMvc.perform(post("/api/result-issues/{issueId}/attachments", firstIssue.issueId())
                        .contentType(MediaType.APPLICATION_JSON).content(attachmentRequest))
                .andExpect(status().isNotFound());
        assertThat(resultIssueAttachmentRepository.count()).isEqualTo(attachmentCount);

        Event secondEvent = createOpenInquiryEvent("Attachment issue two", "attachment-issue-two");
        importPublishedFixture(
                secondEvent.getId(), "attachment-two.csv",
                singleInquiryBibCsv("Второй", "notstarted").getBytes(StandardCharsets.UTF_8)
        );
        CreatedIssueAccess secondIssue = createMissingIssueAccess(secondEvent.getId(), "1100", "1990-01-10");
        mockMvc.perform(post("/api/result-issues/{issueId}/attachments", secondIssue.issueId())
                        .header("X-Result-Issue-Upload-Token", firstIssue.token())
                        .contentType(MediaType.APPLICATION_JSON).content(attachmentRequest))
                .andExpect(status().isNotFound());

        String secondAttachmentResponse = mockMvc.perform(post(
                        "/api/result-issues/{issueId}/attachments", secondIssue.issueId())
                        .header("X-Result-Issue-Upload-Token", secondIssue.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(attachmentRequest.replace("evidence.mp4", "second.mp4")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long secondAttachmentId = objectMapper.readTree(secondAttachmentResponse).get("attachmentId").asLong();
        assertThat(objectMapper.readTree(firstAttachmentResponse).get("uploadUrl").asText())
                .isNotEqualTo(objectMapper.readTree(secondAttachmentResponse).get("uploadUrl").asText());
        assertThat(resultIssueAttachmentRepository.findAllByIssueRequest_IdOrderByCreatedAtAscIdAsc(
                firstIssue.issueId())).hasSize(1);
        assertThat(resultIssueAttachmentRepository.findAllByIssueRequest_IdOrderByCreatedAtAscIdAsc(
                secondIssue.issueId())).hasSize(1);
        mockMvc.perform(post(
                        "/api/result-issues/{issueId}/attachments/{attachmentId}/confirm",
                        firstIssue.issueId(), secondAttachmentId)
                        .header("X-Result-Issue-Upload-Token", firstIssue.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(
                        "/api/result-issues/{issueId}/attachments/{attachmentId}/authorize",
                        firstIssue.issueId(), firstAttachment.getId())
                        .header("X-Result-Issue-Upload-Token", firstIssue.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentId").value(firstAttachment.getId()))
                .andExpect(jsonPath("$.uploadMethod").value("PUT"))
                .andExpect(jsonPath("$.uploadUrl").value(org.hamcrest.Matchers.startsWith("https://")));

        int listStatus = mockMvc.perform(get(
                        "/api/result-issues/{issueId}/attachments", firstIssue.issueId()))
                .andReturn().getResponse().getStatus();
        int downloadStatus = mockMvc.perform(get(
                        "/api/result-issues/{issueId}/attachments/{attachmentId}/download",
                        firstIssue.issueId(), firstAttachment.getId()))
                .andReturn().getResponse().getStatus();
        assertThat(listStatus).isIn(401, 403);
        assertThat(downloadStatus).isIn(401, 403);

        mockMvc.perform(post("/api/result-issues/{issueId}/attachments", firstIssue.issueId())
                        .header("X-Result-Issue-Upload-Token", firstIssue.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalFileName":"payload.exe","contentType":"application/x-msdownload",
                                 "sizeBytes":1024}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_TYPE_NOT_ALLOWED"));

        storedIssue.setAttachmentUploadTokenExpiresAt(Instant.now().minusSeconds(1));
        resultIssueRequestRepository.saveAndFlush(storedIssue);
        mockMvc.perform(post("/api/result-issues/{issueId}/attachments", firstIssue.issueId())
                        .header("X-Result-Issue-Upload-Token", firstIssue.token())
                        .contentType(MediaType.APPLICATION_JSON).content(attachmentRequest))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmsStorageObjectAndAutomaticallyScansWhenScannerIsConfigured() throws Exception {
        Event event = createOpenInquiryEvent("Attachment lifecycle", "attachment-lifecycle");
        importPublishedFixture(
                event.getId(), "attachment-lifecycle.csv",
                singleInquiryBibCsv("Файл", "notstarted").getBytes(StandardCharsets.UTF_8)
        );
        CreatedIssueAccess issue = createMissingIssueAccess(event.getId(), "1100", "1990-01-10");
        String createResponse = mockMvc.perform(post(
                        "/api/result-issues/{issueId}/attachments", issue.issueId())
                        .header("X-Result-Issue-Upload-Token", issue.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalFileName":"finish-video.mp4","contentType":"video/mp4",
                                 "sizeBytes":700000000}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long attachmentId = objectMapper.readTree(createResponse).get("attachmentId").asLong();
        ResultIssueAttachment attachment = resultIssueAttachmentRepository.findById(attachmentId).orElseThrow();

        mockMvc.perform(post(
                        "/api/result-issues/{issueId}/attachments/{attachmentId}/confirm",
                        issue.issueId(), attachmentId)
                        .header("X-Result-Issue-Upload-Token", issue.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_OBJECT_NOT_FOUND"));
        attachmentObjectStorage.recordUploadedObject(
                attachment.getStorageKey(), 699_999_999L, "video/mp4", "wrong-size"
        );
        mockMvc.perform(post(
                        "/api/result-issues/{issueId}/attachments/{attachmentId}/confirm",
                        issue.issueId(), attachmentId)
                        .header("X-Result-Issue-Upload-Token", issue.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_OBJECT_MISMATCH"));
        attachmentObjectStorage.recordUploadedObject(
                attachment.getStorageKey(), 700_000_000L, "video/mp4", "etag-clean-candidate"
        );
        mockMvc.perform(post(
                        "/api/result-issues/{issueId}/attachments/{attachmentId}/confirm",
                        issue.issueId(), attachmentId)
                        .header("X-Result-Issue-Upload-Token", issue.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.scanStatus").value("CLEAN"));

        Instant beforeDownload = Instant.now();
        var download = resultIssueAttachmentService.prepareAuthorizedDownload(attachmentId);
        assertThat(download.url().toString()).startsWith("https://object-storage.invalid/download/");
        assertThat(download.expiresAt())
                .isAfter(beforeDownload)
                .isBefore(beforeDownload.plus(Duration.ofMinutes(6)));

        int publicScanStatus = mockMvc.perform(post(
                        "/api/result-issue-attachments/{attachmentId}/scan", attachmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scanStatus\":\"CLEAN\"}"))
                .andReturn().getResponse().getStatus();
        assertThat(publicScanStatus).isIn(401, 403);

        long issuesBeforeDelete = resultIssueRequestRepository.count();
        resultIssueAttachmentService.deleteBinary(attachmentId);
        ResultIssueAttachment deleted = resultIssueAttachmentRepository.findById(attachmentId).orElseThrow();
        assertThat(deleted.getUploadStatus()).isEqualTo(AttachmentUploadStatus.DELETED);
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(attachmentObjectStorage.findObject(deleted.getStorageKey())).isEmpty();
        assertThat(resultIssueRequestRepository.count()).isEqualTo(issuesBeforeDelete);
        assertThat(resultIssueAttachmentRepository.findById(attachmentId)).isPresent();
        assertThatThrownBy(() -> resultIssueAttachmentService.prepareAuthorizedDownload(attachmentId))
                .isInstanceOf(RequestConflictException.class);
    }

    @Test
    void localFakeScannerIsAdminOnlyLeavesUploadStatusIntactAndUnlocksCleanDownload() throws Exception {
        AdminIssueFixture fixture = createAdminIssueFixture("Fake scanner", "fake-scanner", 1);
        ResultIssueRequest issue = fixture.issues().getFirst();
        ResultIssueAttachment attachment = createAdminAttachment(
                issue, "finish-proof.mp4", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.PENDING
        );
        String scanUrl = "/api/admin/dev/result-issue-attachments/{attachmentId}/scan";

        mockMvc.perform(post(scanUrl, attachment.getId()))
                .andExpect(status().isUnauthorized());

        int publicMutationStatus = mockMvc.perform(post(
                        "/api/result-issue-attachments/{attachmentId}/scan", attachment.getId()))
                .andReturn().getResponse().getStatus();
        assertThat(publicMutationStatus).isIn(401, 403);

        mockMvc.perform(post(scanUrl, attachment.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentId").value(attachment.getId()))
                .andExpect(jsonPath("$.uploadStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.scanStatus").value("CLEAN"))
                .andExpect(jsonPath("$.scannedAt").exists());

        ResultIssueAttachment scanned = resultIssueAttachmentRepository.findById(attachment.getId()).orElseThrow();
        assertThat(scanned.getUploadStatus()).isEqualTo(AttachmentUploadStatus.UPLOADED);
        assertThat(scanned.getScanStatus()).isEqualTo(AttachmentScanStatus.CLEAN);
        assertThat(resultIssueAttachmentService.prepareAuthorizedDownload(attachment.getId()).url())
                .hasHost("object-storage.invalid");

        mockMvc.perform(post(scanUrl, attachment.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTACHMENT_SCAN_ALREADY_CLEAN"));
    }

    @Test
    void adminResultIssueQueueIsPaginatedFilteredEventScopedAndCompact() throws Exception {
        AdminIssueFixture fixture = createAdminIssueFixture("Admin queue", "admin-queue", 6);
        ResultIssueAttachment firstAttachment = createAdminAttachment(
                fixture.issues().get(0), "first.jpg", AttachmentUploadStatus.PENDING_UPLOAD,
                AttachmentScanStatus.PENDING
        );
        createAdminAttachment(
                fixture.issues().get(0), "second.mp4", AttachmentUploadStatus.UPLOADED,
                AttachmentScanStatus.CLEAN
        );
        createAdminAttachment(
                fixture.issues().get(1), "third.pdf", AttachmentUploadStatus.UPLOADED,
                AttachmentScanStatus.PENDING
        );
        AdminIssueFixture other = createAdminIssueFixture("Other admin queue", "other-admin-queue", 1);

        String listUrl = "/api/admin/events/{eventId}/result-issue-requests";
        mockMvc.perform(get(listUrl, fixture.event().getId()))
                .andExpect(status().isUnauthorized());

        String firstPageBody = mockMvc.perform(get(listUrl, fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.sort").value("issueId"))
                .andExpect(jsonPath("$.direction").value("asc"))
                .andExpect(jsonPath("$.content[0].issueId").value(fixture.issues().get(0).getId()))
                .andExpect(jsonPath("$.content[1].issueId").value(fixture.issues().get(1).getId()))
                .andExpect(jsonPath("$.content[0].registration.bib").value("Q-1"))
                .andExpect(jsonPath("$.content[0].race.raceName").value("5 km"))
                .andExpect(jsonPath("$.content[0].race.sportFormatId").doesNotExist())
                .andExpect(jsonPath("$.content[0].attachmentCount").value(2))
                .andExpect(jsonPath("$.content[1].attachmentCount").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(firstPageBody).doesNotContain(
                "contactEmail", "message", "downloadUrl", "storageKey", firstAttachment.getStorageKey()
        );

        mockMvc.perform(get(listUrl, fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].issueId").value(fixture.issues().get(2).getId()))
                .andExpect(jsonPath("$.content[1].issueId").value(fixture.issues().get(3).getId()));

        mockMvc.perform(get(listUrl, fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(listUrl, fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("issueType", "RESULT_CORRECTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get(listUrl, fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("correctionReason", "OFFICIAL_TIME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].issueId").value(fixture.issues().get(2).getId()));
        mockMvc.perform(get(listUrl, fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("bib", "Q-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].registration.bib").value("Q-5"));

        Long from = fixture.issues().get(1).getId();
        Long to = fixture.issues().get(3).getId();
        mockMvc.perform(get(listUrl, fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("issueIdFrom", from.toString())
                        .param("issueIdTo", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].issueId").value(from))
                .andExpect(jsonPath("$.content[2].issueId").value(to));
        mockMvc.perform(get(listUrl, fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("issueIdFrom", fixture.issues().get(4).getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(listUrl, fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("issueIdTo", fixture.issues().get(1).getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(listUrl, fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("issueIdFrom", to.toString())
                        .param("issueIdTo", from.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ISSUE_ID_RANGE"));

        mockMvc.perform(get(listUrl, other.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].issueId").value(other.issues().getFirst().getId()));
    }

    @Test
    void adminResultIssueDetailReturnsCurrentDataSnapshotAndAttachmentMetadataOnly() throws Exception {
        AdminIssueFixture fixture = createAdminIssueFixture("Admin detail", "admin-detail", 4);
        ResultIssueRequest issue = fixture.issues().get(2);
        Result currentResult = issue.getResult();
        Registration currentRegistration = issue.getRegistration();
        Duration resultGunBefore = currentResult.getGunTime();
        String registrationNameBefore = currentRegistration.getDisplayName();
        ResultIssueAttachment attachment = createAdminAttachment(
                issue, "finish-proof.mp4", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.CLEAN
        );
        createAdminAttachment(
                fixture.issues().get(3), "other-issue.jpg", AttachmentUploadStatus.PENDING_UPLOAD,
                AttachmentScanStatus.PENDING
        );
        AdminIssueFixture otherEvent = createAdminIssueFixture("Wrong detail event", "wrong-detail-event", 1);

        String detailUrl = "/api/admin/events/{eventId}/result-issue-requests/{issueId}";
        mockMvc.perform(get(detailUrl, fixture.event().getId(), issue.getId()))
                .andExpect(status().isUnauthorized());
        String body = mockMvc.perform(get(detailUrl, fixture.event().getId(), issue.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueId").value(issue.getId()))
                .andExpect(jsonPath("$.eventId").value(fixture.event().getId()))
                .andExpect(jsonPath("$.issueType").value("RESULT_CORRECTION"))
                .andExpect(jsonPath("$.correctionReason").value("OFFICIAL_TIME"))
                .andExpect(jsonPath("$.contactEmail").value("runner3@example.org"))
                .andExpect(jsonPath("$.message").value("Проверка обращения 3"))
                .andExpect(jsonPath("$.claimedGunTimeMs").value(12_003))
                .andExpect(jsonPath("$.observedResultStatus").value("finished"))
                .andExpect(jsonPath("$.registration.registrationId").value(currentRegistration.getId()))
                .andExpect(jsonPath("$.registration.bib").value("Q-3"))
                .andExpect(jsonPath("$.registration.displayName").value(registrationNameBefore))
                .andExpect(jsonPath("$.registration.birthDate").value("1990-01-03"))
                .andExpect(jsonPath("$.registration.sourceCategory").value("Open"))
                .andExpect(jsonPath("$.registration.raceId").value(currentRegistration.getRace().getId()))
                .andExpect(jsonPath("$.registration.sportFormatId").doesNotExist())
                .andExpect(jsonPath("$.result.resultId").value(currentResult.getId()))
                .andExpect(jsonPath("$.result.status").value("finished"))
                .andExpect(jsonPath("$.attachments.length()").value(1))
                .andExpect(jsonPath("$.attachments[0].attachmentId").value(attachment.getId()))
                .andExpect(jsonPath("$.attachments[0].originalFileName").value("finish-proof.mp4"))
                .andExpect(jsonPath("$.attachments[0].uploadStatus").value("UPLOADED"))
                .andExpect(jsonPath("$.attachments[0].scanStatus").value("CLEAN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(
                "storageKey", attachment.getStorageKey(), "storageEtag", "attachmentUploadToken", "binary"
        );

        mockMvc.perform(get(detailUrl, otherEvent.event().getId(), issue.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESULT_ISSUE_NOT_FOUND"));
        assertThat(resultRepository.findById(currentResult.getId()).orElseThrow().getGunTime())
                .isEqualTo(resultGunBefore);
        assertThat(registrationRepository.findById(currentRegistration.getId()).orElseThrow().getDisplayName())
                .isEqualTo(registrationNameBefore);
    }

    @Test
    void adminResultIssueStatusTransitionsAreConditionalAndNeverEditFacts() throws Exception {
        AdminIssueFixture fixture = createAdminIssueFixture("Admin status", "admin-status", 6);
        normalizeFixtureIssuesToNew(fixture.issues());
        ResultIssueRequest first = fixture.issues().get(0);
        Result firstResult = first.getResult();
        Registration firstRegistration = first.getRegistration();
        String resultStatusBefore = firstResult.getStatus();
        Duration resultGunBefore = firstResult.getGunTime();
        String registrationNameBefore = firstRegistration.getDisplayName();
        long resultCountBefore = resultRepository.count();
        long registrationCountBefore = registrationRepository.count();
        String statusUrl = "/api/admin/events/{eventId}/result-issue-requests/{issueId}/status";

        mockMvc.perform(put(statusUrl, fixture.event().getId(), first.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedStatus\":\"NEW\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(statusUrl, fixture.event().getId(), first.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedStatus\":\"NEW\",\"status\":\"IN_PROGRESS\","+
                                "\"comment\":\"Проверяю данные хронометража\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());
        mockMvc.perform(put(statusUrl, fixture.event().getId(), first.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedStatus\":\"IN_PROGRESS\",\"status\":\"RESOLVED\","+
                                "\"comment\":\"Исправление проверено\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());
        assertThat(resultIssueHistoryRepository.findAllByIssueRequest_IdOrderByCreatedAtAscIdAsc(first.getId()))
                .extracting(
                        history -> history.getAction(),
                        history -> history.getFromStatus(),
                        history -> history.getToStatus(),
                        history -> history.getActor(),
                        history -> history.getReason()
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                ResultIssueHistoryAction.STATUS_CHANGED,
                                ResultIssueStatus.NEW,
                                ResultIssueStatus.IN_PROGRESS,
                                ADMIN_USERNAME,
                                "Проверяю данные хронометража"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                ResultIssueHistoryAction.STATUS_CHANGED,
                                ResultIssueStatus.IN_PROGRESS,
                                ResultIssueStatus.RESOLVED,
                                ADMIN_USERNAME,
                                "Исправление проверено"
                        )
                );
        mockMvc.perform(get("/api/admin/events/{eventId}/result-issue-requests/{issueId}",
                        fixture.event().getId(), first.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].reason").value("Проверяю данные хронометража"))
                .andExpect(jsonPath("$.history[1].reason").value("Исправление проверено"));

        transitionStatus(fixture, 1, ResultIssueStatus.NEW, ResultIssueStatus.IN_PROGRESS);
        transitionStatus(fixture, 1, ResultIssueStatus.IN_PROGRESS, ResultIssueStatus.REJECTED);
        transitionStatus(fixture, 2, ResultIssueStatus.NEW, ResultIssueStatus.RESOLVED);
        transitionStatus(fixture, 3, ResultIssueStatus.NEW, ResultIssueStatus.REJECTED);

        mockMvc.perform(put(statusUrl, fixture.event().getId(), first.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedStatus\":\"RESOLVED\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_RESULT_ISSUE_STATUS_TRANSITION"));
        mockMvc.perform(put(statusUrl, fixture.event().getId(), fixture.issues().get(4).getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedStatus\":\"IN_PROGRESS\",\"status\":\"RESOLVED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_ISSUE_ALREADY_CHANGED"));

        ResultIssueRequest concurrent = fixture.issues().get(5);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> resolve = concurrentStatusUpdate(
                ready, start, fixture.event().getId(), concurrent.getId(), ResultIssueStatus.RESOLVED
        );
        Callable<Boolean> reject = concurrentStatusUpdate(
                ready, start, fixture.event().getId(), concurrent.getId(), ResultIssueStatus.REJECTED
        );
        try (var executor = Executors.newFixedThreadPool(2)) {
            var resolveFuture = executor.submit(resolve);
            var rejectFuture = executor.submit(reject);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(
                    resolveFuture.get(20, TimeUnit.SECONDS),
                    rejectFuture.get(20, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(true, false);
        }
        assertThat(resultIssueRequestRepository.findById(concurrent.getId()).orElseThrow().getStatus())
                .isIn(ResultIssueStatus.RESOLVED, ResultIssueStatus.REJECTED);

        Result unchangedResult = resultRepository.findById(firstResult.getId()).orElseThrow();
        Registration unchangedRegistration = registrationRepository.findById(firstRegistration.getId()).orElseThrow();
        assertThat(unchangedResult.getStatus()).isEqualTo(resultStatusBefore);
        assertThat(unchangedResult.getGunTime()).isEqualTo(resultGunBefore);
        assertThat(unchangedRegistration.getDisplayName()).isEqualTo(registrationNameBefore);
        assertThat(resultRepository.count()).isEqualTo(resultCountBefore);
        assertThat(registrationRepository.count()).isEqualTo(registrationCountBefore);
    }

    @Test
    void resultCorrectionUsesExistingMutationWithoutChangingIssueWorkflowOrSnapshot() throws Exception {
        AdminIssueFixture fixture = createAdminIssueFixture("Issue result workspace", "issue-result-workspace", 5);
        ResultIssueRequest issue = fixture.issues().get(4);
        Result result = issue.getResult();
        Duration snapshotGunTime = issue.getObservedGunTime();
        String statusUrl = "/api/admin/events/{eventId}/result-issue-requests/{issueId}/status";

        mockMvc.perform(get("/api/admin/events/{eventId}/result-issue-requests/{issueId}",
                        fixture.event().getId(), issue.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"));
        assertThat(resultIssueRequestRepository.findById(issue.getId()).orElseThrow().getStatus())
                .isEqualTo(ResultIssueStatus.NEW);

        mockMvc.perform(put(statusUrl, fixture.event().getId(), issue.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedStatus\":\"NEW\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/results/{resultId}", result.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "finished",
                                  "gunTimeMs": 18000,
                                  "chipTimeMs": 17500,
                                  "overallPlace": null,
                                  "genderPlace": null,
                                  "categoryPlace": null,
                                  "netOverallPlace": null,
                                  "netGenderPlace": null,
                                  "netCategoryPlace": null
                                }
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/events/{eventId}/result-issue-requests/{issueId}",
                        fixture.event().getId(), issue.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.gunTimeMs").value(18_000))
                .andExpect(jsonPath("$.snapshot.observedGunTimeMs").value(snapshotGunTime.toMillis()));
        ResultIssueRequest unchangedIssue = resultIssueRequestRepository.findById(issue.getId()).orElseThrow();
        assertThat(unchangedIssue.getStatus()).isEqualTo(ResultIssueStatus.IN_PROGRESS);
        assertThat(unchangedIssue.getObservedGunTime()).isEqualTo(snapshotGunTime);
    }

    @Test
    void adminAttachmentDownloadRequiresScopeAndCleanUploadedState() throws Exception {
        AdminIssueFixture fixture = createAdminIssueFixture("Admin download", "admin-download", 3);
        ResultIssueRequest issue = fixture.issues().get(0);
        ResultIssueAttachment clean = createAdminAttachment(
                issue, "clean.mp4", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.CLEAN
        );
        ResultIssueAttachment pending = createAdminAttachment(
                issue, "pending.mp4", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.PENDING
        );
        ResultIssueAttachment infected = createAdminAttachment(
                issue, "infected.mp4", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.INFECTED
        );
        ResultIssueAttachment failed = createAdminAttachment(
                issue, "failed.mp4", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.SCAN_FAILED
        );
        ResultIssueAttachment deleted = createAdminAttachment(
                issue, "deleted.mp4", AttachmentUploadStatus.DELETED, AttachmentScanStatus.PENDING
        );
        ResultIssueAttachment otherIssue = createAdminAttachment(
                fixture.issues().get(1), "other.mp4", AttachmentUploadStatus.UPLOADED,
                AttachmentScanStatus.CLEAN
        );
        AdminIssueFixture otherEvent = createAdminIssueFixture("Other download", "other-download", 1);
        ResultIssueAttachment otherEventAttachment = createAdminAttachment(
                otherEvent.issues().getFirst(), "other-event.mp4", AttachmentUploadStatus.UPLOADED,
                AttachmentScanStatus.CLEAN
        );
        attachmentObjectStorage.recordUploadedObject(clean.getStorageKey(), clean.getSizeBytes(), "video/mp4", "etag");
        String downloadUrl = "/api/admin/events/{eventId}/result-issue-requests/{issueId}"
                + "/attachments/{attachmentId}/download-authorization";

        mockMvc.perform(post(downloadUrl, fixture.event().getId(), issue.getId(), clean.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(downloadUrl, fixture.event().getId(), issue.getId(), clean.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value(
                        org.hamcrest.Matchers.startsWith("https://object-storage.invalid/download/")
                ))
                .andExpect(jsonPath("$.expiresAt").exists());

        for (ResultIssueAttachment unavailable : List.of(pending, infected, failed, deleted)) {
            mockMvc.perform(post(
                            downloadUrl,
                            fixture.event().getId(), issue.getId(), unavailable.getId())
                            .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("RESULT_ISSUE_ATTACHMENT_NOT_AVAILABLE"));
        }
        mockMvc.perform(post(downloadUrl, fixture.event().getId(), issue.getId(), otherIssue.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                        downloadUrl,
                        fixture.event().getId(), issue.getId(), otherEventAttachment.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(downloadUrl, fixture.event().getId(), issue.getId(), Long.MAX_VALUE)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void archivesAnIssueWithoutDeletingHistoryOrBlockingANewPublicIssue() throws Exception {
        Event event = createOpenInquiryEvent("Stage E archive", "stage-e-archive");
        event = eventRepository.findById(event.getId()).orElseThrow();
        event.setLocation("Perm");
        event = eventRepository.saveAndFlush(event);
        importPublishedFixture(
                event.getId(), "stage-e-archive.csv",
                singleInquiryBibCsv("Архивный", "notstarted").getBytes(StandardCharsets.UTF_8)
        );
        Registration registration = registrationRepository
                .findAllByRaceEventIdAndBib(event.getId(), "1100").getFirst();
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();
        long revision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        Map<String, Object> registrationBefore = jdbcTemplate.queryForMap(
                "SELECT * FROM registrations WHERE id=?", registration.getId());
        Map<String, Object> resultBefore = jdbcTemplate.queryForMap(
                "SELECT * FROM results WHERE id=?", result.getId());

        CreatedIssueAccess firstAccess = createMissingIssueAccess(event.getId(), "1100", "1990-01-10");
        ResultIssueRequest first = resultIssueRequestRepository.findById(firstAccess.issueId()).orElseThrow();
        assertThat(first.getSnapshotOrigin()).isEqualTo(ResultIssueSnapshotOrigin.CAPTURED_AT_CREATION);
        assertThat(first.getSnapshotEventName()).isEqualTo("Stage E archive");
        assertThat(first.getSnapshotEventLocation()).isEqualTo("Perm");
        assertThat(first.getSnapshotEventStartsAt()).isEqualTo(event.getStartsAt());
        assertThat(first.getSnapshotSportFormatId()).isNull();
        assertThat(first.getSnapshotSportFormatName()).isNull();
        assertThat(first.getSnapshotSportFormatCode()).isNull();
        assertThat(first.getSnapshotRaceId()).isEqualTo(registration.getRace().getId());
        assertThat(first.getSnapshotRaceName()).isEqualTo(registration.getRace().getName());
        assertThat(first.getSnapshotRaceCode()).isEqualTo(registration.getRace().getSourceCode());
        assertThat(first.getSnapshotBib()).isEqualTo("1100");
        assertThat(first.getSnapshotDisplayName()).isEqualTo("Архивный Участник");
        assertThat(first.getSnapshotRanking()).isEqualTo("[]");
        assertThat(first.getSnapshotImportBatchId()).isEqualTo(registration.getImportBatch().getId());
        assertThat(first.getSnapshotSourceRowNumber()).isEqualTo(registration.getSourceRowNumber());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='result_issue_requests'
                  AND column_name='snapshot_birth_date'
                """, Integer.class)).isZero();
        assertThat(resultIssueHistoryRepository.findAllByIssueRequest_IdOrderByCreatedAtAscIdAsc(first.getId()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getAction()).isEqualTo(ResultIssueHistoryAction.CREATED);
                    assertThat(history.getFromStatus()).isNull();
                    assertThat(history.getToStatus()).isEqualTo(ResultIssueStatus.NEW);
                    assertThat(history.getActor()).isEqualTo("PUBLIC_API");
                });

        ResultIssueAttachment attachment = createAdminAttachment(
                first, "stage-e.pdf", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.CLEAN
        );
        String attachmentStorageKey = attachment.getStorageKey();
        assertThat(resultInquiryService.lookup(event.getId(), "1100").activeIssue()).isNull();
        assertThat(resultInquiryService.verify(event.getId(), "1100", LocalDate.parse("1990-01-10"))
                .activeIssue().issueId()).isEqualTo(first.getId());

        String archiveUrl = "/api/admin/events/{eventId}/result-issue-requests/{issueId}/archive";
        String archiveBody = "{\"reason\":\"MANUAL\"}";
        mockMvc.perform(post(archiveUrl, event.getId(), first.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(archiveBody))
                .andExpect(status().isUnauthorized());
        Event otherEvent = createPublishedEvent("Stage E other", "stage-e-other");
        mockMvc.perform(post(archiveUrl, otherEvent.getId(), first.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(archiveBody))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(archiveUrl, event.getId(), first.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(archiveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.queueArchivedAt").exists())
                .andExpect(jsonPath("$.queueArchivedBy").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.queueArchiveReason").value("MANUAL"));

        ResultIssueRequest archived = resultIssueRequestRepository.findById(first.getId()).orElseThrow();
        Instant archivedAt = archived.getQueueArchivedAt();
        assertThat(archived.getStatus()).isEqualTo(ResultIssueStatus.NEW);
        assertThat(archived.getQueueArchivedBy()).isEqualTo(ADMIN_USERNAME);
        assertThat(archived.getQueueArchiveReason()).isEqualTo(ResultIssueArchiveReason.MANUAL);
        assertThat(archived.getQueueArchivedImportOperationId()).isNull();
        assertThat(resultIssueHistoryRepository.countByIssueRequest_IdAndAction(
                first.getId(), ResultIssueHistoryAction.QUEUE_ARCHIVED)).isOne();

        mockMvc.perform(post(archiveUrl, event.getId(), first.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(archiveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueArchivedAt").exists());
        assertThat(resultIssueRequestRepository.findById(first.getId()).orElseThrow().getQueueArchivedAt())
                .isEqualTo(archivedAt);
        assertThat(resultIssueHistoryRepository.countByIssueRequest_IdAndAction(
                first.getId(), ResultIssueHistoryAction.QUEUE_ARCHIVED)).isOne();

        mockMvc.perform(get("/api/admin/events/{eventId}/result-issue-requests", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/admin/events/{eventId}/result-issue-requests", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("queueScope", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].issueId").value(first.getId()))
                .andExpect(jsonPath("$.content[0].queueArchivedAt").exists());
        mockMvc.perform(get("/api/admin/events/{eventId}/result-issue-requests/{issueId}",
                        event.getId(), first.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archive.queueArchiveReason").value("MANUAL"))
                .andExpect(jsonPath("$.snapshot.origin").value("CAPTURED_AT_CREATION"))
                .andExpect(jsonPath("$.snapshot.bib").value("1100"))
                .andExpect(jsonPath("$.snapshot.rankingAchievements").isArray())
                .andExpect(jsonPath("$.attachments[0].attachmentId").value(attachment.getId()));
        mockMvc.perform(post(
                        "/api/admin/events/{eventId}/result-issue-requests/{issueId}"
                                + "/attachments/{attachmentId}/download-authorization",
                        event.getId(), first.getId(), attachment.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").exists());

        assertThat(resultInquiryService.verify(event.getId(), "1100", LocalDate.parse("1990-01-10"))
                .activeIssue()).isNull();

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM registrations WHERE id=?", registration.getId()))
                .isEqualTo(registrationBefore);
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM results WHERE id=?", result.getId()))
                .isEqualTo(resultBefore);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revision);

        byte[] updateBytes = singleInquiryBibCsv("Текущий", "notstarted")
                .getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto updatePreview = importPreviewService.preview(
                event.getId(), "stage-e-update.csv", updateBytes, ImportOperationMode.UPDATE_EXISTING,
                List.of(registration.getRace().getId()), 100, ADMIN_USERNAME
        );
        importApplyService.apply(event.getId(), updatePreview.operationId(), updateBytes, ADMIN_USERNAME);
        Registration currentRegistration = registrationRepository.findById(registration.getId()).orElseThrow();
        assertThat(currentRegistration.getDisplayName()).isEqualTo("Текущий Участник");
        assertThat(resultIssueRequestRepository.findById(first.getId()).orElseThrow().getSnapshotDisplayName())
                .isEqualTo("Архивный Участник");
        Map<String, Object> registrationAfterUpdate = jdbcTemplate.queryForMap(
                "SELECT * FROM registrations WHERE id=?", registration.getId());
        Map<String, Object> resultAfterUpdate = jdbcTemplate.queryForMap(
                "SELECT * FROM results WHERE id=?", result.getId());
        long revisionAfterUpdate = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        assertThat(revisionAfterUpdate).isGreaterThan(revision);

        CreatedIssueAccess secondAccess = createMissingIssueAccess(event.getId(), "1100", "1990-01-10");
        assertThat(secondAccess.issueId()).isNotEqualTo(first.getId());
        ResultIssueRequest second = resultIssueRequestRepository.findById(secondAccess.issueId()).orElseThrow();
        assertThat(second.getSnapshotDisplayName()).isEqualTo("Текущий Участник");
        assertThat(second.getSnapshotOrigin()).isEqualTo(ResultIssueSnapshotOrigin.CAPTURED_AT_CREATION);
        assertThat(resultInquiryService.verify(event.getId(), "1100", LocalDate.parse("1990-01-10"))
                .activeIssue().issueId()).isEqualTo(secondAccess.issueId());

        String statusUrl = "/api/admin/events/{eventId}/result-issue-requests/{issueId}/status";
        mockMvc.perform(put(statusUrl, event.getId(), first.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedStatus\":\"NEW\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mockMvc.perform(put(statusUrl, event.getId(), first.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedStatus\":\"IN_PROGRESS\",\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk());
        ResultIssueRequest archivedAfterStatus = resultIssueRequestRepository.findById(first.getId()).orElseThrow();
        assertThat(archivedAfterStatus.getQueueArchivedAt()).isEqualTo(archivedAt);
        assertThat(resultIssueHistoryRepository.countByIssueRequest_IdAndAction(
                first.getId(), ResultIssueHistoryAction.STATUS_CHANGED)).isOne();

        assertThat(resultIssueRequestRepository.count()).isEqualTo(2);
        assertThat(resultIssueAttachmentRepository.findById(attachment.getId()).orElseThrow())
                .satisfies(saved -> {
                    assertThat(saved.getStorageKey()).isEqualTo(attachmentStorageKey);
                    assertThat(saved.getUploadStatus()).isEqualTo(AttachmentUploadStatus.UPLOADED);
                    assertThat(saved.getScanStatus()).isEqualTo(AttachmentScanStatus.CLEAN);
                });
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM registrations WHERE id=?", registration.getId()))
                .isEqualTo(registrationAfterUpdate);
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM results WHERE id=?", result.getId()))
                .isEqualTo(resultAfterUpdate);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revisionAfterUpdate);
    }

    @Test
    void usesOneActivePredicateForWorkflowQueueAndFutureRaceScope() throws Exception {
        AdminIssueFixture fixture = createAdminIssueFixture("Stage E predicate", "stage-e-predicate", 6);
        ResultIssueRequest archivedNew = fixture.issues().get(4);
        ResultIssueRequest archivedInProgress = fixture.issues().get(5);
        resultIssueLifecycleService.archiveManual(
                fixture.event().getId(), archivedNew.getId(), ResultIssueArchiveReason.MANUAL, ADMIN_USERNAME);
        resultIssueLifecycleService.archiveManual(
                fixture.event().getId(), archivedInProgress.getId(), ResultIssueArchiveReason.OTHER, ADMIN_USERNAME);

        assertThat(resultIssueRequestRepository
                .findFirstByRegistration_IdAndQueueArchivedAtIsNullAndStatusInOrderByIdAsc(
                        fixture.registrations().get(0).getId(), ResultIssueStatus.activeStatuses())).isPresent();
        assertThat(resultIssueRequestRepository
                .findFirstByRegistration_IdAndQueueArchivedAtIsNullAndStatusInOrderByIdAsc(
                        fixture.registrations().get(1).getId(), ResultIssueStatus.activeStatuses())).isPresent();
        for (int index : List.of(2, 3, 4, 5)) {
            assertThat(resultIssueRequestRepository
                    .findFirstByRegistration_IdAndQueueArchivedAtIsNullAndStatusInOrderByIdAsc(
                            fixture.registrations().get(index).getId(), ResultIssueStatus.activeStatuses()))
                    .isEmpty();
        }

        Long raceId = fixture.registrations().getFirst().getRace().getId();
        assertThat(resultIssueRequestRepository.findActiveByEventAndRaceScope(
                fixture.event().getId(), List.of(raceId), ResultIssueStatus.activeStatuses()
        )).extracting(ResultIssueRequest::getId)
                .containsExactly(fixture.issues().get(0).getId(), fixture.issues().get(1).getId());

        mockMvc.perform(get("/api/admin/events/{eventId}/result-issue-requests", fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mockMvc.perform(get("/api/admin/events/{eventId}/result-issue-requests", fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("queueScope", "ARCHIVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/admin/events/{eventId}/result-issue-requests", fixture.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("queueScope", "ALL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    void keepsEveryPublicProtocolRaceScopedAndHonorsRaceVisibility() throws Exception {
        Event event = createPublishedEvent("Race separation", "race-separation");
        assertThat(importPublishedFixture(event.getId(), "races.csv",
                raceSeparationCsv().getBytes(StandardCharsets.UTF_8)).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);

        Race raceA = raceRepository.findByEventIdAndSourceCode(event.getId(), "Race A").orElseThrow();
        Race raceB = raceRepository.findByEventIdAndSourceCode(event.getId(), "Race B").orElseThrow();
        Race raceC = raceRepository.findByEventIdAndSourceCode(event.getId(), "Race C").orElseThrow();
        long registrationsBeforeVisibilityChanges = registrationRepository.countByRaceEventId(event.getId());
        long resultsBeforeVisibilityChanges = resultRepository.countByRegistrationRaceEventId(event.getId());

        for (Race race : List.of(raceA, raceB, raceC)) {
            awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                    RankingBasis.CHIP_TIME, PrimaryStandingMode.ALL, 1, false,
                    AgeCalculationMode.EVENT_DATE, 0, false), ADMIN_USERNAME);
        }

        mockMvc.perform(get("/api/events/slug/{slug}", event.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sportFormats").doesNotExist())
                .andExpect(jsonPath("$.races.length()").value(3))
                .andExpect(jsonPath("$.races[0].id").value(raceA.getId()))
                .andExpect(jsonPath("$.races[0].name").value("Race A"))
                .andExpect(jsonPath("$.races[0].displayOrder").value(0))
                .andExpect(jsonPath("$.races[1].id").value(raceB.getId()))
                .andExpect(jsonPath("$.races[1].name").value("Race B"))
                .andExpect(jsonPath("$.races[1].displayOrder").value(1))
                .andExpect(jsonPath("$.races[2].id").value(raceC.getId()))
                .andExpect(jsonPath("$.races[2].name").value("Race C"))
                .andExpect(jsonPath("$.races[2].displayOrder").value(2));

        mockMvc.perform(get("/api/events/{eventId}/results", event.getId()))
                .andExpect(status().isBadRequest());

        for (Race race : List.of(raceA, raceB, raceC)) {
            var protocol = resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                    0, 20, "place", "asc");
            assertThat(protocol.content()).singleElement().satisfies(item -> {
                assertThat(item.raceId()).isEqualTo(race.getId());
                assertThat(item.raceName()).isEqualTo(race.getName());
                assertThat(item.rankingAchievements()).singleElement().satisfies(achievement -> {
                    assertThat(achievement.place()).isEqualTo(1);
                    assertThat(achievement.type()).isEqualTo(ru.sportsresults.domain.RankingAchievementType.ABSOLUTE);
                });
            });
        }

        raceAdminService.update(event.getId(), raceB.getId(), new ru.sportsresults.api.dto.UpsertRaceRequest(
                raceB.getSourceCode(), raceB.getName(), raceB.getSlug(), raceB.getDistanceMeters(), raceB.getStartsAt(),
                raceB.getDisplayOrder(), false), ADMIN_USERNAME);
        Long hiddenResultId = jdbcTemplate.queryForObject("""
                SELECT result.id
                FROM results result
                JOIN registrations registration ON registration.id = result.registration_id
                WHERE registration.race_id = ?
                """, Long.class, raceB.getId());
        mockMvc.perform(get("/api/events/slug/{slug}", event.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sportFormats").doesNotExist())
                .andExpect(jsonPath("$.races.length()").value(2))
                .andExpect(jsonPath("$.races[0].id").value(raceA.getId()))
                .andExpect(jsonPath("$.races[0].displayOrder").value(0))
                .andExpect(jsonPath("$.races[1].id").value(raceC.getId()))
                .andExpect(jsonPath("$.races[1].displayOrder").value(2));
        mockMvc.perform(get("/api/events/{eventId}/races", event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(raceA.getId()))
                .andExpect(jsonPath("$[1].id").value(raceC.getId()));
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId())
                        .param("raceId", raceB.getId().toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/results/{resultId}", hiddenResultId))
                .andExpect(status().isNotFound());
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.RACE, raceB.getId()))
                .anyMatch(change -> change.getFieldName().equals("publicVisible")
                        && change.getOldValue().equals("true") && change.getNewValue().equals("false"));

        mockMvc.perform(get("/api/admin/events/{eventId}/results", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        assertThat(registrationRepository.countByRaceEventId(event.getId()))
                .isEqualTo(registrationsBeforeVisibilityChanges);
        assertThat(resultRepository.countByRegistrationRaceEventId(event.getId()))
                .isEqualTo(resultsBeforeVisibilityChanges);
        assertThat(awardPolicyService.get(raceB.getId()).raceId()).isEqualTo(raceB.getId());
    }

    @Test
    void generatedXlsxTemplateFlowsThroughAnalysisPreviewAndExistingApplyPipeline() throws Exception {
        Event event = createPublishedEvent("Stage J synthetic", "stage-j-synthetic");
        Race first = createDraftRace(event, "SYN-A", 0);
        Race second = createDraftRace(event, "SYN-B", 1);

        MvcResult download = mockMvc.perform(get("/api/admin/events/{eventId}/imports/template.xlsx", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        byte[] workbook = populateGeneratedTemplate(download.getResponse().getContentAsByteArray(), first.getId());
        MockMultipartFile file = new MockMultipartFile(
                "file", "stage-j-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook
        );

        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/analyze", event.getId())
                        .file(file).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sportsResultsTemplate").value(true))
                .andExpect(jsonPath("$.readyForValidation").value(true))
                .andExpect(jsonPath("$.resolvedRaceIds.length()").value(2))
                .andExpect(jsonPath("$.resolvedRaceIds[0]").value(first.getId()))
                .andExpect(jsonPath("$.resolvedRaceIds[1]").value(second.getId()));

        MvcResult preview = mockMvc.perform(multipart(
                                "/api/admin/events/{eventId}/imports/preview", event.getId())
                        .file(file)
                        .param("mode", "ADD_NEW")
                        .param("raceIds", first.getId().toString(), second.getId().toString())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingErrorsPresent").value(false))
                .andExpect(jsonPath("$.totals.totalRows").value(1))
                .andExpect(jsonPath("$.totals.newCount").value(1))
                .andReturn();
        String operationId = objectMapper.readTree(preview.getResponse().getContentAsByteArray())
                .get("operationId").asText();

        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/{operationId}/apply", event.getId(), operationId)
                        .file(file).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insertedCount").value(1))
                .andExpect(jsonPath("$.updatedCount").value(0));

        assertThat(registrationRepository.findAllByRaceIdAndBib(first.getId(), "SYN-XLSX-1"))
                .singleElement().satisfies(registration -> {
                    assertThat(registration.getFirstName()).isEqualTo("Анна");
                    assertThat(registration.getLastName()).isEqualTo("Тестова");
                    assertThat(registration.getBirthDate()).isEqualTo(LocalDate.of(1992, 4, 3));
                });
        assertThat(resultRepository.findAll()).singleElement().satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo("finished");
            assertThat(result.getGunTime()).isEqualTo(Duration.ofHours(1).plusMinutes(2).plusSeconds(3));
            assertThat(result.getChipTime()).isEqualTo(Duration.ofHours(1).plusMinutes(1).plusSeconds(58));
        });
        assertThat(importBatchRepository.count()).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT input_config <> '{}'::jsonb FROM import_operations WHERE id=?::uuid",
                Boolean.class, operationId
        )).isTrue();
    }

    @Test
    void mappingProfileCrudAndExactHeaderReuseWorkThroughAdminApi() throws Exception {
        Event event = createPublishedEvent("Profile event", "profile-event");
        Race race = createDraftRace(event, "PROFILE", 0);
        MockMultipartFile file = new MockMultipartFile(
                "file", "supplier.csv", "text/csv",
                "custom_bib;custom_status;custom_name\nSYN-42;finished;Synthetic Runner\n"
                        .getBytes(StandardCharsets.UTF_8)
        );

        MvcResult firstAnalysis = mockMvc.perform(multipart(
                                "/api/admin/events/{eventId}/imports/analyze", event.getId())
                        .file(file).param("targetRaceId", race.getId().toString())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyForValidation").value(false))
                .andReturn();
        String signature = objectMapper.readTree(firstAnalysis.getResponse().getContentAsByteArray())
                .get("headerSignature").asText();

        String createBody = objectMapper.writeValueAsString(Map.of(
                "name", "Synthetic timer",
                "fileType", "CSV",
                "headerSignature", signature,
                "mappings", Map.of(
                        "custom_bib", "BIB", "custom_status", "STATUS", "custom_name", "FULL_NAME"
                )
        ));
        MvcResult created = mockMvc.perform(post("/api/admin/import-mapping-profiles")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Synthetic timer"))
                .andReturn();
        long profileId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();

        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/analyze", event.getId())
                        .file(file).param("targetRaceId", race.getId().toString())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readyForValidation").value(true))
                .andExpect(jsonPath("$.suggestedProfile.id").value(profileId))
                .andExpect(jsonPath("$.columnMappings.custom_bib").value("BIB"))
                .andExpect(jsonPath("$.columnMappings.custom_status").value("STATUS"));

        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/analyze", event.getId())
                        .file(file)
                        .param("targetRaceId", race.getId().toString())
                        .param("options", objectMapper.writeValueAsString(Map.of(
                                "targetRaceId", race.getId(),
                                "columnMappings", Map.of("custom_name", "LAST_NAME"),
                                "raceMappings", Map.of(),
                                "saveRaceMappings", false
                        )))
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedProfile.id").value(profileId))
                .andExpect(jsonPath("$.columnMappings.custom_name").value("LAST_NAME"))
                .andExpect(jsonPath("$.readyForValidation").value(true));

        MockMultipartFile incompatible = new MockMultipartFile(
                "file", "other.csv", "text/csv",
                "custom_bib;different_status\nSYN-43;finished\n".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/analyze", event.getId())
                        .file(incompatible).param("targetRaceId", race.getId().toString())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedProfile").doesNotExist())
                .andExpect(jsonPath("$.readyForValidation").value(false));

        mockMvc.perform(get("/api/admin/import-mapping-profiles")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(delete("/api/admin/import-mapping-profiles/{profileId}", profileId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/import-mapping-profiles")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void confirmedExternalRaceCodeIsAuditedReusedAndConflictsBeforeApply() throws Exception {
        Event event = createPublishedEvent("Race mapping event", "race-mapping-event");
        Race first = createDraftRace(event, "INTERNAL-A", 0);
        Race second = createDraftRace(event, "TAKEN", 1);
        byte[] csv = "start,bib,status\nEXT-A,SYN-MAP-1,finished\n".getBytes(StandardCharsets.UTF_8);
        String options = objectMapper.writeValueAsString(Map.of(
                "columnMappings", Map.of("start", "RACE", "bib", "BIB", "status", "STATUS"),
                "raceMappings", Map.of("EXT-A", first.getId()),
                "saveRaceMappings", true
        ));
        MockMultipartFile file = new MockMultipartFile("file", "multi.csv", "text/csv", csv);

        MvcResult preview = mockMvc.perform(multipart(
                                "/api/admin/events/{eventId}/imports/preview", event.getId())
                        .file(file)
                        .param("mode", "ADD_NEW")
                        .param("raceIds", first.getId().toString())
                        .param("options", options)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockingErrorsPresent").value(false))
                .andReturn();
        String operationId = objectMapper.readTree(preview.getResponse().getContentAsByteArray())
                .get("operationId").asText();
        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/{operationId}/apply", event.getId(), operationId)
                        .file(file).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insertedCount").value(1));

        assertThat(raceRepository.findById(first.getId()).orElseThrow().getSourceCode()).isEqualTo("EXT-A");
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.RACE, first.getId()
        )).anySatisfy(change -> {
            assertThat(change.getFieldName()).isEqualTo("sourceCode");
            assertThat(change.getOldValue()).isEqualTo("INTERNAL-A");
            assertThat(change.getNewValue()).isEqualTo("EXT-A");
        });

        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/analyze", event.getId())
                        .file(file).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.raceValues[0].sourceValue").value("EXT-A"))
                .andExpect(jsonPath("$.raceValues[0].raceId").value(first.getId()))
                .andExpect(jsonPath("$.raceValues[0].automatic").value(true));

        byte[] conflictCsv = "start,bib,status\nTAKEN,SYN-MAP-2,finished\n".getBytes(StandardCharsets.UTF_8);
        String conflictOptions = objectMapper.writeValueAsString(Map.of(
                "columnMappings", Map.of("start", "RACE", "bib", "BIB", "status", "STATUS"),
                "raceMappings", Map.of("TAKEN", first.getId()),
                "saveRaceMappings", true
        ));
        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports/preview", event.getId())
                        .file(new MockMultipartFile("file", "conflict.csv", "text/csv", conflictCsv))
                        .param("mode", "ADD_NEW")
                        .param("raceIds", first.getId().toString())
                        .param("options", conflictOptions)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RACE_SOURCE_CODE_CONFLICT"));
        assertThat(registrationRepository.countByRaceEventId(event.getId())).isOne();
        assertThat(raceRepository.findById(second.getId()).orElseThrow().getSourceCode()).isEqualTo("TAKEN");
    }

    @Test
    void importsM52AndTreatsSameSuccessfulFileAsIdempotent() throws Exception {
        Event event = createPublishedEvent("M52 2025", "m52-2025");
        byte[] csv = Files.readAllBytes(sample("results_m52_2025.csv"));

        ImportReportDto first = importPublishedFixture(event.getId(), "results_m52_2025.csv", csv);

        assertThat(first.status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        assertThat(first.totalRows()).isEqualTo(31);
        assertThat(first.importedRows()).isEqualTo(31);
        assertThat(raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId()))
                .extracting(Race::getSourceCode)
                .containsExactly("10 km", "42.2 km");
        assertThat(categoryRepository.findAllByRaceEventId(event.getId())).isNotEmpty();
        assertThat(registrationRepository.countByRaceEventId(event.getId())).isEqualTo(31);
        assertThat(resultRepository.countByRegistrationRaceEventId(event.getId())).isEqualTo(31);
        assertThat(count("results", "status = 'notstarted'")).isPositive();
        assertThat(count("results", "status = 'finished'")).isPositive();
        assertThat(count("results", "status = 'notstarted' AND gun_time_ms IS NULL AND chip_time_ms IS NULL"))
                .isPositive();
        assertThat(count("results", "status = 'running'")).isPositive();
        assertThat(count("results", "status = 'quarantine'")).isOne();

        ImportReportDto second = importPublishedFixture(event.getId(), "renamed.csv", csv);

        assertThat(second.duplicateFile()).isTrue();
        assertThat(second.batchId()).isEqualTo(first.batchId());
        assertThat(importBatchRepository.count()).isOne();
        assertThat(resultRepository.countByRegistrationRaceEventId(event.getId())).isEqualTo(31);
    }

    @Test
    void importsGonkaWithNullableIdentityAndDuplicateBib() throws Exception {
        Event event = createPublishedEvent("Гонка героев 2026", "gonka-2026");
        ImportReportDto report = importPublishedFixture(
                event.getId(),
                "results_gonka2026.csv",
                Files.readAllBytes(sample("results_gonka2026.csv"))
        );

        assertThat(report.status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        assertThat(report.importedRows()).isEqualTo(16);
        assertThat(raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId()))
                .extracting(Race::getSourceCode)
                .containsExactly("mass", "teams", "champ", "corp");
        assertThat(categoryRepository.findAllByRaceEventId(event.getId())).isEmpty();
        Race mass = raceRepository.findByEventIdAndSourceCode(event.getId(), "mass").orElseThrow();
        assertThat(registrationRepository.findAllByRaceIdAndBib(mass.getId(), "SYN-DUP")).hasSize(2);
        assertThat(count("registrations", "gender IS NULL")).isPositive();
        assertThat(count("registrations", "birth_date IS NULL")).isPositive();
        assertThat(count("results", "status = 'disqualified'")).isOne();
    }

    @Test
    void invalidReplacementKeepsPreviousSnapshot() {
        Event event = createPublishedEvent("Atomic event", "atomic-event");
        ImportReportDto valid = importPublishedFixture(
                event.getId(),
                "valid.csv",
                oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8)
        );
        assertThat(valid.status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        long revisionAfterSuccess = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        assertThat(revisionAfterSuccess).isOne();
        Long originalResultId = resultRepository.findAll().getFirst().getId();

        ImportReportDto invalid = importPublishedFixture(
                event.getId(),
                "invalid.csv",
                oneRowCsv("1000.5").getBytes(StandardCharsets.UTF_8)
        );

        assertThat(invalid.status()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(resultRepository.count()).isOne();
        assertThat(resultRepository.findAll().getFirst().getId()).isEqualTo(originalResultId);
        assertThat(importBatchRepository.count()).isEqualTo(2);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revisionAfterSuccess);
    }

    @Test
    void combinesFiltersPaginationAndNullsLastAgainstSyntheticM52Data() throws Exception {
        Event event = createPublishedEvent("M52 filters", "m52-filters");
        assertThat(importPublishedFixture(
                event.getId(),
                "results_m52_2025.csv",
                Files.readAllBytes(sample("results_m52_2025.csv"))
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race tenKilometers = raceRepository.findByEventIdAndSourceCode(event.getId(), "10 km").orElseThrow();
        Category category = categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(tenKilometers.getId()).getFirst();

        PageResponse<ResultListItemDto> femaleByChipTime = resultQueryService.search(
                event.getId(), tenKilometers.getId(), null, null, "female", null, null,
                0, 200, "chipTime", "asc"
        );
        assertThat(femaleByChipTime.content()).isNotEmpty();
        assertThat(femaleByChipTime.content()).allMatch(item -> "female".equalsIgnoreCase(item.gender()));
        assertAscendingNullsLast(femaleByChipTime.content().stream().map(ResultListItemDto::chipTimeMs).toList());

        Registration withDobAndSourceCategory = registrationRepository.findAll().stream()
                .filter(registration -> registration.getRace().getId().equals(tenKilometers.getId()))
                .filter(registration -> registration.getBirthDate() != null)
                .filter(registration -> registration.getSourceCategory() != null)
                .findFirst()
                .orElseThrow();
        assertThat(withDobAndSourceCategory.getCategory())
                .as("DOB must not fall back to an unstructured source category")
                .isNull();

        Registration named = registrationRepository.findAll().stream()
                .filter(registration -> registration.getLastName() != null)
                .findFirst()
                .orElseThrow();
        assertThat(resultQueryService.search(
                event.getId(), named.getRace().getId(), named.getLastName(), null, null, null, null,
                0, 50, "displayName", "asc"
        ).content()).anyMatch(item -> named.getLastName().equals(item.lastName()));
        assertThat(resultQueryService.search(
                event.getId(), named.getRace().getId(), null, named.getBib(), null, null, null,
                0, 50, "bib", "asc"
        ).content()).anyMatch(item -> named.getBib().equals(item.bib()));
        assertThat(resultQueryService.search(
                event.getId(), null, null, null, null, null, "notstarted",
                0, 10, "displayName", "desc", RankingBasis.CHIP_TIME
        ).content()).allMatch(item -> "notstarted".equals(item.status()));

        PageResponse<ResultListItemDto> firstPage = resultQueryService.search(
                event.getId(), tenKilometers.getId(), null, null, null, null, null,
                0, 10, "chipTime", "desc"
        );
        PageResponse<ResultListItemDto> secondPage = resultQueryService.search(
                event.getId(), tenKilometers.getId(), null, null, null, null, null,
                1, 10, "chipTime", "desc"
        );
        assertThat(firstPage.content()).hasSize(10);
        assertThat(secondPage.content()).hasSize(10);
        assertThat(firstPage.content().getFirst().chipTimeMs())
                .isGreaterThanOrEqualTo(firstPage.content().getLast().chipTimeMs());
        assertThat(firstPage.content()).doesNotContainAnyElementsOf(secondPage.content());

        PageResponse<ResultListItemDto> adminPage = resultQueryService.search(
                event.getId(), tenKilometers.getId(), null, null, null, null, null,
                0, 10, "chipTime", "asc", RankingBasis.CHIP_TIME
        );
        int lastPageNumber = Math.max(0, adminPage.totalPages() - 1);
        PageResponse<ResultListItemDto> lastAscendingPage = resultQueryService.search(
                event.getId(), tenKilometers.getId(), null, null, null, null, null,
                lastPageNumber, 10, "chipTime", "asc", RankingBasis.CHIP_TIME
        );
        assertThat(lastAscendingPage.content().getLast().chipTimeMs()).isNull();

        mockMvc.perform(get("/api/events/{eventId}/results", event.getId())
                        .param("raceId", tenKilometers.getId().toString())
                        .param("gender", "female")
                        .param("sort", "chipTime")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].gender").value("female"));

        mockMvc.perform(get("/api/events/{eventId}/categories", event.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/events/{eventId}/categories", event.getId())
                        .param("raceId", tenKilometers.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        awardPolicyService.upsert(tenKilometers.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.ALL, 3, true,
                AgeCalculationMode.EVENT_DATE, 3, false
        ), ADMIN_USERNAME);
        mockMvc.perform(get("/api/events/{eventId}/categories", event.getId())
                        .param("raceId", tenKilometers.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(category.getId()))
                .andExpect(jsonPath("$[0].raceId").value(tenKilometers.getId()))
                .andExpect(jsonPath("$[0].raceName").value("10 km"));
    }

    @Test
    void securesAdminApiEditsEntitiesAndWritesFieldAudit() throws Exception {
        mockMvc.perform(get("/api/events")).andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("Admin event", "admin-event")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/events")
                .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("Admin event", "admin-event")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("admin-event"));
        Event event = eventRepository.findBySlug("admin-event").orElseThrow();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "admin.csv",
                "text/csv",
                oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/admin/events/{eventId}/imports", event.getId())
                        .file(file)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));

        Result result = resultRepository.findAll().getFirst();
        Registration registration = registrationRepository.findAll().getFirst();
        String resultUpdate = """
                {
                  "status": "reviewed",
                  "gunTimeMs": 1200,
                  "chipTimeMs": 1100,
                  "overallPlace": 2,
                  "genderPlace": 1,
                  "categoryPlace": 1,
                  "netOverallPlace": 2,
                  "netGenderPlace": 1,
                  "netCategoryPlace": 1
                }
                """;
        mockMvc.perform(put("/api/admin/results/{resultId}", result.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultUpdate))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/admin/results/{resultId}", result.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultUpdate))
                .andExpect(status().isNoContent());

        String registrationUpdate = """
                {
                  "displayName": "Исправленное имя",
                  "firstName": "Исправленное",
                  "lastName": "Имя",
                  "birthDate": "1991-02-03",
                  "gender": "female",
                  "bib": "A-NEW",
                  "sourceCategory": "Open",
                  "entryKind": "PERSON"
                }
                """;
        mockMvc.perform(put("/api/admin/registrations/{registrationId}", registration.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationUpdate))
                .andExpect(status().isNoContent());

        Result updatedResult = resultRepository.findById(result.getId()).orElseThrow();
        Registration updatedRegistration = registrationRepository.findById(registration.getId()).orElseThrow();
        assertThat(updatedResult.getStatus()).isEqualTo("reviewed");
        assertThat(updatedResult.getChipTime()).isEqualTo(Duration.ofMillis(1_100));
        assertThat(updatedRegistration.getDisplayName()).isEqualTo("Исправленное имя");
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.RESULT,
                result.getId()
        )).extracting(change -> change.getActor()).containsOnly(ADMIN_USERNAME);
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.REGISTRATION,
                registration.getId()
        )).isNotEmpty();

        mockMvc.perform(get("/api/admin/results/{resultId}", result.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("reviewed"))
                .andExpect(jsonPath("$.chipTimeMs").value(1_100));

        long auditCount = changeLogRepository.count();
        ImportReportDto replacement = importPublishedFixture(
                event.getId(),
                "replacement.csv",
                oneRowCsv("2000.0").getBytes(StandardCharsets.UTF_8)
        );
        assertThat(replacement.status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        assertThat(changeLogRepository.count()).isEqualTo(auditCount);
        assertThat(resultRepository.findById(result.getId())).isEmpty();
    }

    @Test
    void adminCorrectionAssignsOnlyRaceCategoryAndOfficialRankingUsesCurrentResultFacts() {
        Event event = createPublishedEvent("Admin correction ranking", "admin-correction-ranking");
        importPublishedFixture(
                event.getId(), "ranking.csv", rankingCsv().getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        Race otherRace = raceRepository.findByEventIdAndSourceCode(event.getId(), "10 km").orElseThrow();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 0, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);

        Category manualCategory = new Category();
        manualCategory.setRace(race);
        manualCategory.setSourceName("MANUAL-30");
        manualCategory.setDisplayName("30–39 · мужчины");
        manualCategory.setDisplayOrder(50);
        manualCategory.setEnabled(true);
        manualCategory = categoryRepository.saveAndFlush(manualCategory);
        Category otherRaceCategory = categoryRepository
                .findAllByRaceIdOrderByDisplayOrderAsc(otherRace.getId()).getFirst();

        Registration registration = registrationRepository
                .findAllByRaceIdAndBib(race.getId(), "1").getFirst();
        adminResultService.updateRegistration(registration.getId(), new UpdateRegistrationRequest(
                registration.getDisplayName(), registration.getFirstName(), registration.getLastName(),
                registration.getBirthDate(), registration.getGender(), registration.getBib(),
                registration.getSourceCategory(), null, registration.getEntryKind(), manualCategory.getId()
        ), ADMIN_USERNAME);
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getCategory().getId())
                .isEqualTo(manualCategory.getId());

        assertThatThrownBy(() -> adminResultService.updateRegistration(
                registration.getId(), new UpdateRegistrationRequest(
                        registration.getDisplayName(), registration.getFirstName(), registration.getLastName(),
                        registration.getBirthDate(), registration.getGender(), registration.getBib(),
                        registration.getSourceCategory(), null, registration.getEntryKind(), otherRaceCategory.getId()
                ), ADMIN_USERNAME
        )).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("registration race");

        var before = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc");
        assertThat(achievement(before.content(), "Ган Лидер", "ABSOLUTE").place()).isEqualTo(1);
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();
        Integer importedOverallPlace = result.getOverallPlace();
        adminResultService.updateResult(result.getId(), new UpdateResultRequest(
                result.getStatus(), 5_000L, result.getChipTime().toMillis(),
                result.getOverallPlace(), result.getGenderPlace(), result.getCategoryPlace(),
                result.getNetOverallPlace(), result.getNetGenderPlace(), result.getNetCategoryPlace()
        ), ADMIN_USERNAME);

        var after = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc");
        assertThat(achievement(after.content(), "Чип Лидер", "ABSOLUTE").place()).isEqualTo(1);
        assertThat(achievement(after.content(), "Ган Лидер", "ABSOLUTE").place()).isEqualTo(2);
        assertThat(resultRepository.findById(result.getId()).orElseThrow().getOverallPlace())
                .isEqualTo(importedOverallPlace);
    }

    @Test
    void rejectsInvalidAdminTimeAndReturnsNotFoundWithoutStackTrace() throws Exception {
        Event event = createPublishedEvent("Validation event", "validation-event");
        importPublishedFixture(event.getId(), "validation.csv", oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId())
                        .param("raceId", race.getId().toString()).param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));

        String negativeTime = """
                {
                  "status": "finished",
                  "gunTimeMs": null,
                  "chipTimeMs": -1,
                  "overallPlace": null,
                  "genderPlace": null,
                  "categoryPlace": null,
                  "netOverallPlace": null,
                  "netGenderPlace": null,
                  "netCategoryPlace": null
                }
                """;
        mockMvc.perform(put("/api/admin/results/999")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(negativeTime))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        String valid = negativeTime.replace("-1", "1");
        mockMvc.perform(put("/api/admin/results/999")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESULT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Result not found"));
    }

    @Test
    void catalogsSeriesAcrossYearsCitiesAndExactDates() throws Exception {
        EventSeries heroes = createSeries("Гонка Героев", "hero-race");
        EventSeries m52 = createSeries("M52", "m52");
        createEvent(heroes, "Москва 2026", "heroes-moscow-2026", "Москва", "2026-06-15T06:00:00Z");
        createEvent(heroes, "Петербург 2026", "heroes-spb-2026", "Санкт-Петербург", "2026-06-15T06:00:00Z");
        createEvent(heroes, "Казань 2026", "heroes-kazan-2026", "Казань", "2026-07-20T06:00:00Z");
        createEvent(heroes, "Москва 2027", "heroes-moscow-2027", "Москва", "2027-06-14T06:00:00Z");
        createEvent(m52, "M52 2025", "m52-2025", "Екатеринбург", "2025-09-07T05:00:00Z");

        var sameDay = eventService.searchPublishedEvents(2026, heroes.getId(), null, LocalDate.of(2026, 6, 15), 0, 20);
        assertThat(sameDay.content()).extracting(item -> item.location())
                .containsExactly("Санкт-Петербург", "Москва");
        var moscow = eventService.searchPublishedEvents(2026, heroes.getId(), "москва", LocalDate.of(2026, 6, 15), 0, 20);
        assertThat(moscow.content()).singleElement().satisfies(item -> assertThat(item.slug()).isEqualTo("heroes-moscow-2026"));
        assertThat(eventService.searchPublishedEvents(2026, heroes.getId(), null, LocalDate.of(2026, 1, 1), 0, 20).content()).isEmpty();

        var options = eventService.filterOptions(2026, heroes.getId(), null, null);
        assertThat(options.years()).contains(2027, 2026);
        assertThat(options.eventSeries()).extracting(item -> item.name()).containsExactly("Гонка Героев");
        assertThat(options.cities()).containsExactlyInAnyOrder("Москва", "Санкт-Петербург", "Казань");

        mockMvc.perform(get("/api/events")
                        .param("year", "2026")
                        .param("eventSeriesId", heroes.getId().toString())
                        .param("date", "2026-06-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].eventSeriesName").value("Гонка Героев"));
    }

    @Test
    void selectsContextualPlaceAndDoesNotMixUnselectedCategories() throws Exception {
        Event event = createPublishedEvent("M52 contextual", "m52-contextual");
        importPublishedFixture(event.getId(), "results_m52_2025.csv", Files.readAllBytes(sample("results_m52_2025.csv")));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "10 km").orElseThrow();
        var category = categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(race.getId()).getFirst();

        var allGun = resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "place", "asc", RankingBasis.GUN_TIME);
        assertThat(allGun.content()).allSatisfy(item -> assertThat(item.place()).isEqualTo(item.overallPlace()));

        var allChip = resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "place", "asc", RankingBasis.CHIP_TIME);
        assertThat(allChip.content()).allSatisfy(item -> assertThat(item.place()).isEqualTo(item.netOverallPlace()));

        var womenChip = resultQueryService.search(event.getId(), race.getId(), null, null, "female", null, null,
                0, 50, "place", "asc", RankingBasis.CHIP_TIME);
        assertThat(womenChip.content().getFirst().place()).isEqualTo(1);
        assertThat(womenChip.content()).allSatisfy(item -> assertThat(item.place()).isEqualTo(item.netGenderPlace()));

        var categoryChip = resultQueryService.search(event.getId(), race.getId(), null, null, "female", category.getId(), null,
                0, 50, "place", "asc", RankingBasis.CHIP_TIME);
        assertThat(categoryChip.content()).allSatisfy(item -> assertThat(item.place()).isEqualTo(item.netCategoryPlace()));
        assertAscendingNullsLast(categoryChip.content().stream().map(item -> item.place() == null ? null : item.place().longValue()).toList());
    }

    @Test
    void sortsCyrillicBeforeLatinAndKeepsBibAsExactString() {
        Event event = createPublishedEvent("Names", "names");
        importPublishedFixture(event.getId(), "names.csv", namesCsv().getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();

        var byName = resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "displayName", "asc", RankingBasis.GUN_TIME);
        assertThat(byName.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("Александр", "Евгений", "Ярослав", "Alexey", "John");
        assertThat(resultQueryService.search(event.getId(), race.getId(), "ЕКАТ", null, null, null, null,
                0, 20, "displayName", "asc", RankingBasis.GUN_TIME).content()).isEmpty();
        assertThat(resultQueryService.search(event.getId(), race.getId(), null, "00123", null, null, null,
                0, 20, "bib", "asc", RankingBasis.GUN_TIME).content())
                .extracting(ResultListItemDto::bib).containsExactly("00123");
        assertThat(resultQueryService.search(event.getId(), race.getId(), null, "A123", null, null, null,
                0, 20, "bib", "asc", RankingBasis.GUN_TIME).content())
                .extracting(ResultListItemDto::bib).containsExactly("A123");
    }

    @Test
    void searchesNamesByPartsOrderCasePaginationAndCombinedFilters() {
        Event event = createPublishedEvent("Search", "search");
        importPublishedFixture(event.getId(), "search.csv", searchCsv().getBytes(StandardCharsets.UTF_8));
        Race fiveKm = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        var women = categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(fiveKm.getId()).stream()
                .filter(category -> category.getDisplayName().equals("Women"))
                .findFirst().orElseThrow();

        assertThat(resultQueryService.search(event.getId(), null, "мар", null, null, null, null,
                0, 20, "displayName", "asc", RankingBasis.CHIP_TIME).content())
                .extracting(ResultListItemDto::displayName)
                .containsExactly("Марина Иванова", "Мария Петрова");
        assertThat(resultQueryService.search(event.getId(), null, "ИВАНОВ", null, null, null, null,
                0, 20, "displayName", "asc", RankingBasis.CHIP_TIME).content())
                .extracting(ResultListItemDto::lastName)
                .containsOnly("Иванов", "Иванова");
        assertThat(resultQueryService.search(event.getId(), null, "петрова мария", null, null, null, null,
                0, 20, "displayName", "asc", RankingBasis.CHIP_TIME).content())
                .extracting(ResultListItemDto::displayName)
                .containsExactly("Мария Петрова");

        var firstPage = resultQueryService.search(event.getId(), null, "иван", null, null, null, null,
                0, 1, "displayName", "asc", RankingBasis.CHIP_TIME);
        var secondPage = resultQueryService.search(event.getId(), null, "иван", null, null, null, null,
                1, 1, "displayName", "asc", RankingBasis.CHIP_TIME);
        assertThat(firstPage.totalElements()).isEqualTo(4);
        assertThat(firstPage.content()).hasSize(1).doesNotContainAnyElementsOf(secondPage.content());

        assertThat(resultQueryService.search(event.getId(), fiveKm.getId(), "иван", null, null, null, null,
                0, 20, "displayName", "asc", RankingBasis.CHIP_TIME).content())
                .extracting(ResultListItemDto::raceName).containsOnly("5 km");
        assertThat(resultQueryService.search(event.getId(), null, "иван", null, "male", null, null,
                0, 20, "displayName", "asc", RankingBasis.CHIP_TIME).content())
                .extracting(ResultListItemDto::gender).containsOnly("male");
        assertThat(resultQueryService.search(event.getId(), fiveKm.getId(), "мар", null, "female", women.getId(), null,
                0, 20, "displayName", "asc", RankingBasis.CHIP_TIME).content())
                .extracting(ResultListItemDto::displayName)
                .containsExactly("Марина Иванова", "Мария Петрова");
    }

    @Test
    void embedsOfficialAchievementsWithExclusionsMultipleStandingsAndGenderRanks() {
        Event event = createPublishedEvent("Awards", "awards");
        importPublishedFixture(event.getId(), "awards.csv", awardsCsv().getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        var target = categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(race.getId()).stream()
                .filter(category -> category.getDisplayName().equals("Target"))
                .findFirst().orElseThrow();

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.ALL, 3, true,
                AgeCalculationMode.EVENT_DATE, 3, true
        ), ADMIN_USERNAME);
        var excluded = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, target.getId(), null,
                0, 20, "chipTime", "asc"
        );
        assertThat(excluded.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("B", "C", "D");
        assertThat(achievement(excluded.content(), "B", "CATEGORY").place()).isEqualTo(1);
        assertThat(achievement(excluded.content(), "C", "CATEGORY").place()).isEqualTo(2);
        assertThat(achievement(excluded.content(), "D", "CATEGORY").place()).isEqualTo(3);
        assertThat(achievement(excluded.content(), "B", "CATEGORY").label())
                .isEqualTo("1 место · Target");
        assertThat(resultQueryService.searchAdmin(
                event.getId(), race.getId(), null, null, null, target.getId(), null,
                0, 20, "chipTime", "asc", RankingBasis.CHIP_TIME
        ).content()).extracting(ResultListItemDto::displayName)
                .containsExactly("A", "Y", "B", "C", "D");

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.ALL, 3, true,
                AgeCalculationMode.EVENT_DATE, 3, false
        ), ADMIN_USERNAME);
        var doubleAwards = resultQueryService.search(
                event.getId(), race.getId(), "A", null, null, target.getId(), null,
                0, 20, "chipTime", "asc"
        );
        assertThat(doubleAwards.content()).singleElement().satisfies(result -> {
            assertThat(result.rankingAchievements()).extracting(item -> item.type().name(), RankingAchievementDto::place)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("ABSOLUTE", 2),
                            org.assertj.core.groups.Tuple.tuple("CATEGORY", 1)
                    );
        });
        var filteredCategory = resultQueryService.search(
                event.getId(), race.getId(), "B", null, null, target.getId(), null,
                0, 20, "displayName", "asc"
        );
        assertThat(achievement(filteredCategory.content(), "B", "CATEGORY").place()).isEqualTo(3);

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.BY_GENDER, 1, true,
                AgeCalculationMode.EVENT_DATE, 3, false
        ), ADMIN_USERNAME);
        var women = resultQueryService.search(event.getId(), race.getId(), null, null, "female", null, null,
                0, 20, "chipTime", "asc");
        var men = resultQueryService.search(event.getId(), race.getId(), null, null, "male", null, null,
                0, 20, "chipTime", "asc");
        assertThat(achievement(women.content(), "A", "GENDER").place()).isEqualTo(1);
        assertThat(achievement(men.content(), "X", "GENDER").place()).isEqualTo(1);

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.NONE, 0, true,
                AgeCalculationMode.EVENT_DATE, 3, false
        ), ADMIN_USERNAME);
        var categoriesOnly = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, target.getId(), null,
                0, 20, "chipTime", "asc"
        );
        assertThat(categoriesOnly.content()).allSatisfy(result -> {
            assertThat(result.rankingAchievements())
                    .allMatch(achievement -> achievement.type().name().equals("CATEGORY"));
            assertThat(result.rankingAchievements()).isNotEmpty();
        });
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.AWARD_POLICY, awardPolicyService.get(race.getId()).id()
        )).isNotEmpty();
    }

    @Test
    void buildsCompleteStandingsAndAppliesCategoryPolicyWithoutReimport() {
        Event event = createPublishedEvent("Full standing", "full-standing");
        importPublishedFixture(
                event.getId(), "full-standing.csv", fullStandingCsv(60).getBytes(StandardCharsets.UTF_8)
        );
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        long importCount = importBatchRepository.count();

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.BY_GENDER, 3, false,
                AgeCalculationMode.EVENT_DATE, 3, true
        ), ADMIN_USERNAME);
        var primaryOnly = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 100, "gunTime", "asc"
        );
        assertThat(primaryOnly.content()).hasSize(60);
        for (int place = 1; place <= 60; place++) {
            String name = "Участник %02d".formatted(place);
            RankingAchievementDto primary = achievement(primaryOnly.content(), name, "GENDER");
            assertThat(primary.place()).isEqualTo(place);
            assertThat(primary.prize()).isEqualTo(place <= 3);
            assertThat(item(primaryOnly.content(), name).rankingAchievements())
                    .noneMatch(value -> value.type().name().equals("CATEGORY"));
        }
        assertThat(eventService.listPublishedEventRaces(event.getId()))
                .filteredOn(value -> value.id().equals(race.getId()))
                .allMatch(value -> !value.categoryStandingEnabled());
        assertThat(eventService.listPublishedEventCategories(event.getId(), race.getId())).isEmpty();

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.BY_GENDER, 3, true,
                AgeCalculationMode.EVENT_DATE, 3, true
        ), ADMIN_USERNAME);
        var withCategories = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 100, "gunTime", "asc"
        );
        for (int primaryPlace = 1; primaryPlace <= 60; primaryPlace++) {
            String name = "Участник %02d".formatted(primaryPlace);
            assertThat(achievement(withCategories.content(), name, "GENDER").place()).isEqualTo(primaryPlace);
            if (primaryPlace <= 3) {
                assertThat(item(withCategories.content(), name).rankingAchievements())
                        .noneMatch(value -> value.type().name().equals("CATEGORY"));
            } else {
                RankingAchievementDto category = achievement(withCategories.content(), name, "CATEGORY");
                assertThat(category.place()).isEqualTo(primaryPlace - 3);
                assertThat(category.prize()).isEqualTo(primaryPlace <= 6);
            }
        }
        assertThat(eventService.listPublishedEventRaces(event.getId()))
                .filteredOn(value -> value.id().equals(race.getId()))
                .allMatch(value -> value.categoryStandingEnabled());
        assertThat(eventService.listPublishedEventCategories(event.getId(), race.getId()))
                .extracting(value -> value.name()).containsExactly("18+ М");

        var filtered = resultQueryService.search(
                event.getId(), race.getId(), "Участник 08", null, null, null, null,
                0, 20, "displayName", "asc"
        );
        assertThat(achievement(filtered.content(), "Участник 08", "CATEGORY").place()).isEqualTo(5);
        var pageTwelve = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                11, 5, "gunTime", "asc"
        );
        assertThat(pageTwelve.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("Участник 56", "Участник 57", "Участник 58", "Участник 59", "Участник 60");
        assertThat(achievement(pageTwelve.content(), "Участник 57", "GENDER").place()).isEqualTo(57);
        assertThat(achievement(pageTwelve.content(), "Участник 57", "CATEGORY").place()).isEqualTo(54);

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.BY_GENDER, 3, false,
                AgeCalculationMode.EVENT_DATE, 3, true
        ), ADMIN_USERNAME);
        var disabledAgain = resultQueryService.search(
                event.getId(), race.getId(), "Участник 60", null, null, null, null,
                0, 20, "gunTime", "asc"
        );
        assertThat(achievement(disabledAgain.content(), "Участник 60", "GENDER").place()).isEqualTo(60);
        assertThat(item(disabledAgain.content(), "Участник 60").rankingAchievements())
                .noneMatch(value -> value.type().name().equals("CATEGORY"));
        assertThat(eventService.listPublishedEventRaces(event.getId()))
                .filteredOn(value -> value.id().equals(race.getId()))
                .allMatch(value -> !value.categoryStandingEnabled());
        assertThat(eventService.listPublishedEventCategories(event.getId(), race.getId())).isEmpty();
        assertThat(importBatchRepository.count()).isEqualTo(importCount);
    }

    @Test
    void givesEqualTimesTheSameCompetitionRankWithoutUsingResultIdAsSportingRule() {
        Event event = createPublishedEvent("Ties", "ties");
        importPublishedFixture(event.getId(), "ties.csv", tieStandingCsv().getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 1, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);

        var results = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc"
        );
        assertThat(results.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("A", "B", "C");
        assertThat(resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc"
        ).content()).extracting(ResultListItemDto::displayName)
                .containsExactly("A", "B", "C");
        assertThat(achievement(results.content(), "A", "ABSOLUTE").place()).isEqualTo(1);
        assertThat(achievement(results.content(), "B", "ABSOLUTE").place()).isEqualTo(1);
        assertThat(achievement(results.content(), "A", "ABSOLUTE").prize()).isTrue();
        assertThat(achievement(results.content(), "B", "ABSOLUTE").prize()).isTrue();
        assertThat(achievement(results.content(), "C", "ABSOLUTE").place()).isEqualTo(3);
        assertThat(achievement(results.content(), "C", "ABSOLUTE").prize()).isFalse();
    }

    @Test
    void exposesCategoryMetadataOnlyWhenRacePolicyEnablesStanding() {
        Event event = createPublishedEvent("Categories", "categories");
        assertThat(importPublishedFixture(
                event.getId(),
                "categories.csv",
                categorySemanticsCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);

        Race baseRace = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        Race ageRace = raceRepository.findByEventIdAndSourceCode(event.getId(), "10 km").orElseThrow();
        assertThat(eventService.listPublishedEventRaces(event.getId()))
                .allMatch(item -> !item.categoryStandingEnabled());
        assertThat(eventService.listPublishedEventCategories(event.getId(), null)).isEmpty();
        assertThat(eventService.listPublishedEventCategories(event.getId(), baseRace.getId())).isEmpty();
        assertThat(eventService.listPublishedEventCategories(event.getId(), ageRace.getId())).isEmpty();
        awardPolicyService.upsert(ageRace.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.NONE, 0, true,
                AgeCalculationMode.EVENT_DATE, 1, false
        ), ADMIN_USERNAME);
        assertThat(eventService.listPublishedEventRaces(event.getId()))
                .filteredOn(item -> item.id().equals(baseRace.getId()))
                .allMatch(item -> !item.categoryStandingEnabled());
        assertThat(eventService.listPublishedEventRaces(event.getId()))
                .filteredOn(item -> item.id().equals(ageRace.getId()))
                .allMatch(item -> item.categoryStandingEnabled());
        assertThat(eventService.listPublishedEventCategories(event.getId(), ageRace.getId()))
                .extracting(item -> item.name())
                .containsExactly("30–39 Ж");
        assertThat(resultQueryService.search(
                event.getId(), ageRace.getId(), null, null, null, null, null,
                0, 20, "chipTime", "asc"
        ).content().getFirst().rankingAchievements().getFirst().label()).isEqualTo("1 место · 30–39 Ж");
        assertThat(categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(baseRace.getId()))
                .extracting(category -> category.getSourceName())
                .containsExactly("18+ Male", "18+ Female");
    }

    @Test
    void derivesEffectiveCategoryFromDobAndRecalculatesWithoutReimport() throws Exception {
        EventSeries series = createSeries("DOB Series", "dob-series");
        Event event = createEvent(
                series,
                "DOB Event",
                "dob-event",
                "Test City",
                "2027-06-15T00:00:00Z"
        );
        assertThat(importPublishedFixture(
                event.getId(),
                "dob.csv",
                dobCategoryCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        var ageThirtyToThirtyNine = categoryRepository.findByRaceIdAndSourceName(
                race.getId(), "30-39 Male"
        ).orElseThrow();
        raceResultsPublicationService.draft(
                event.getId(), race.getId(), "Configure age categories", ADMIN_USERNAME
        );

        mockMvc.perform(put("/api/admin/races/{raceId}/categories/{categoryId}",
                        race.getId(), ageThirtyToThirtyNine.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(
                                "30-39 Male", "30–39 М", 30, 39,
                                CategoryGender.MALE, 0, true
                        )))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/races/{raceId}/categories", race.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(
                                "40-49 Male", "40–49 М", 40, 49,
                                CategoryGender.MALE, 1, true
                        )))
                .andExpect(status().isCreated());

        long importCount = importBatchRepository.count();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.BY_GENDER, 3, true,
                AgeCalculationMode.EVENT_DATE, 3, false
        ), ADMIN_USERNAME);
        raceResultsPublicationService.publish(event.getId(), race.getId(), ADMIN_USERNAME);

        Registration registration = registrationRepository.findAll().getFirst();
        Result result = resultRepository.findAll().getFirst();
        assertThat(registration.getSourceCategory()).isEqualTo("30-39 Male");
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getCategory().getDisplayName())
                .isEqualTo("30–39 М");
        assertThat(achievement(resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "place", "asc"
        ).content(), "DOB Runner", "CATEGORY").label()).isEqualTo("1 место · 30–39 М");

        mockMvc.perform(get("/api/results/{resultId}", result.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").doesNotExist())
                .andExpect(jsonPath("$.sourceCategory").doesNotExist())
                .andExpect(jsonPath("$.category.name").value("30–39 М"));
        mockMvc.perform(get("/api/admin/results/{resultId}", result.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").value("1987-06-20"))
                .andExpect(jsonPath("$.sourceCategory").value("30-39 Male"))
                .andExpect(jsonPath("$.effectiveCategory.name").value("30–39 М"));

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.BY_GENDER, 3, true,
                AgeCalculationMode.END_OF_EVENT_YEAR, 3, false
        ), ADMIN_USERNAME);
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getCategory().getDisplayName())
                .isEqualTo("40–49 М");

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.BY_GENDER, 3, true,
                AgeCalculationMode.EVENT_DATE, 3, false
        ), ADMIN_USERNAME);
        raceResultsPublicationService.draft(
                event.getId(), race.getId(), "Correct Event date", ADMIN_USERNAME
        );
        eventService.updateEvent(event.getId(), new UpdateEventRequest(
                series.getId(), event.getName(), event.getSlug(),
                Instant.parse("2027-06-21T00:00:00Z"), null,
                event.getLocation(), "Europe/Moscow", EventPublicationStatus.PUBLISHED
        ), ADMIN_USERNAME);
        ResultRecalculationPreviewDto datePreview = resultRecalculationService.preview(
                event.getId(), List.of(race.getId()), 10, ADMIN_USERNAME
        );
        resultRecalculationService.apply(event.getId(), datePreview.operationId(), ADMIN_USERNAME);
        raceResultsPublicationService.publish(event.getId(), race.getId(), ADMIN_USERNAME);
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getCategory().getDisplayName())
                .isEqualTo("40–49 М");

        mockMvc.perform(put("/api/admin/registrations/{registrationId}", registration.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "DOB Runner",
                                  "firstName": "DOB",
                                  "lastName": "Runner",
                                  "birthDate": "1992-01-01",
                                  "gender": "male",
                                  "bib": "1",
                                  "sourceCategory": "30-39 Male",
                                  "entryKind": "UNKNOWN"
                                }
                                """))
                .andExpect(status().isNoContent());
        Registration corrected = registrationRepository.findById(registration.getId()).orElseThrow();
        assertThat(corrected.getCategory().getDisplayName()).isEqualTo("30–39 М");
        assertThat(corrected.getSourceCategory()).isEqualTo("30-39 Male");
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.REGISTRATION, registration.getId()
        )).extracting(AdminChangeLog::getFieldName).contains("birthDate");

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.BY_GENDER, 3, false,
                AgeCalculationMode.EVENT_DATE, 3, false
        ), ADMIN_USERNAME);
        assertThat(resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "place", "asc"
        ).content().getFirst().rankingAchievements())
                .noneMatch(achievement -> achievement.type().name().equals("CATEGORY"));
        assertThat(eventService.listPublishedEventCategories(event.getId(), race.getId())).isEmpty();
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getSourceCategory())
                .isEqualTo("30-39 Male");
        assertThat(importBatchRepository.count()).isEqualTo(importCount);
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.AWARD_POLICY, awardPolicyService.get(race.getId()).id()
        )).extracting(AdminChangeLog::getFieldName).contains("ageCalculationMode", "categoryEnabled");
    }

    @Test
    void sourceCategoryFallbackIsOfficialAndCategoryAdminRejectsAmbiguousRanges() throws Exception {
        EventSeries series = createSeries("Fallback Series", "fallback-series");
        Event event = createEvent(
                series,
                "Fallback Event",
                "fallback-event",
                "Test City",
                "2027-06-15T00:00:00Z"
        );
        assertThat(importPublishedFixture(
                event.getId(),
                "fallback.csv",
                sourceCategoryFallbackCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        var maleCategory = categoryRepository.findByRaceIdAndSourceName(
                race.getId(), "30-39 Male"
        ).orElseThrow();
        raceResultsPublicationService.draft(
                event.getId(), race.getId(), "Configure source fallback categories", ADMIN_USERNAME
        );

        mockMvc.perform(put("/api/admin/races/{raceId}/categories/{categoryId}",
                        race.getId(), maleCategory.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(
                                "30-39 Male", "30–39 М", 30, 39,
                                CategoryGender.MALE, 0, true
                        )))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/races/{raceId}/categories", race.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(
                                "30-39 Female", "30–39 Ж", 30, 39,
                                CategoryGender.FEMALE, 1, true
                        )))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/races/{raceId}/categories", race.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(
                                "35-45 Male", "35–45 М", 35, 45,
                                CategoryGender.MALE, 2, true
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_RANGE_OVERLAP"));

        long importCount = importBatchRepository.count();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.ALL, 0, true,
                AgeCalculationMode.EVENT_DATE, 3, false
        ), ADMIN_USERNAME);
        raceResultsPublicationService.publish(event.getId(), race.getId(), ADMIN_USERNAME);
        Registration fallback = registrationRepository.findAll().stream()
                .filter(item -> item.getDisplayName().equals("Fallback Runner"))
                .findFirst().orElseThrow();
        Registration withoutCategory = registrationRepository.findAll().stream()
                .filter(item -> item.getDisplayName().equals("Primary Only"))
                .findFirst().orElseThrow();
        assertThat(fallback.getBirthDate()).isNull();
        assertThat(fallback.getSourceCategory()).isEqualTo("30-39 Male");
        assertThat(registrationRepository.findById(fallback.getId()).orElseThrow().getCategory().getDisplayName())
                .isEqualTo("30–39 М");
        assertThat(registrationRepository.findById(withoutCategory.getId()).orElseThrow().getCategory()).isNull();
        var protocol = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "place", "asc"
        );
        assertThat(achievement(protocol.content(), "Fallback Runner", "CATEGORY").place()).isEqualTo(1);
        assertThat(item(protocol.content(), "Primary Only").rankingAchievements())
                .noneMatch(achievement -> achievement.type().name().equals("CATEGORY"));

        raceResultsPublicationService.draft(
                event.getId(), race.getId(), "Category deletion safeguard", ADMIN_USERNAME
        );
        mockMvc.perform(delete("/api/admin/races/{raceId}/categories/{categoryId}",
                        race.getId(), maleCategory.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
        Registration afterDelete = registrationRepository.findById(fallback.getId()).orElseThrow();
        assertThat(afterDelete.getCategory().getId()).isEqualTo(maleCategory.getId());
        assertThat(afterDelete.getSourceCategory()).isEqualTo("30-39 Male");
        assertThat(importBatchRepository.count()).isEqualTo(importCount);
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.CATEGORY, maleCategory.getId()
        )).extracting(AdminChangeLog::getFieldName).contains("minAge", "maxAge", "gender")
                .doesNotContain("deleted");
    }

    @Test
    void publicProtocolRestrictsStatusesWhileAdminRetainsFullAccess() throws Exception {
        Event event = createPublishedEvent("Statuses", "statuses");
        assertThat(importPublishedFixture(
                event.getId(),
                "statuses.csv",
                statusesCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();

        mockMvc.perform(get("/api/events/{eventId}/results", event.getId()).param("raceId", race.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId()).param("raceId", race.getId().toString()).param("status", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId()).param("raceId", race.getId().toString()).param("status", "finished"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId()).param("raceId", race.getId().toString()).param("status", "disqualified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId()).param("raceId", race.getId().toString()).param("status", "running"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId()).param("raceId", race.getId().toString()).param("name", "Скрытый Running"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        Result hidden = resultRepository.findAll().stream()
                .filter(item -> item.getStatus().equals("running"))
                .findFirst()
                .orElseThrow();
        mockMvc.perform(get("/api/results/{resultId}", hidden.getId()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/results/{resultId}", hidden.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("running"));
        mockMvc.perform(get("/api/admin/events/{eventId}/results", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("status", "running"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.ALL, 3, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);
        var publicResults = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "place", "asc"
        );
        assertThat(item(publicResults.content(), "Видимый Финиш").rankingAchievements())
                .singleElement().extracting(RankingAchievementDto::place).isEqualTo(1);
        assertThat(item(publicResults.content(), "Видимый DQ").rankingAchievements()).isEmpty();
    }

    @Test
    void displaySortingNeverRecalculatesAchievementsAndPolicyDrivesRankingBasis() throws Exception {
        Event event = createPublishedEvent("Ranking", "ranking");
        importPublishedFixture(event.getId(), "ranking.csv", rankingCsv().getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        Race secondRace = raceRepository.findByEventIdAndSourceCode(event.getId(), "10 km").orElseThrow();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 2, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);

        var chipSorted = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "chipTime", "asc"
        );
        assertThat(chipSorted.content().getFirst().displayName()).isEqualTo("Чип Лидер");
        assertThat(achievement(chipSorted.content(), "Чип Лидер", "ABSOLUTE").place()).isEqualTo(2);
        var gunSorted = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc"
        );
        assertThat(gunSorted.content().getFirst().displayName()).isEqualTo("Ган Лидер");
        assertThat(achievement(gunSorted.content(), "Ган Лидер", "ABSOLUTE").place()).isEqualTo(1);
        var filtered = resultQueryService.search(
                event.getId(), race.getId(), "Чип", null, null, null, null,
                0, 20, "gunTime", "asc"
        );
        assertThat(achievement(filtered.content(), "Чип Лидер", "ABSOLUTE").place()).isEqualTo(2);

        raceResultsPublicationService.draft(
                event.getId(), secondRace.getId(), "Ranking basis change", ADMIN_USERNAME
        );
        mockMvc.perform(put("/api/admin/races/{raceId}/public-settings", secondRace.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicRankingBasis\":\"GUN_TIME\"}"))
                .andExpect(status().isOk());
        ResultRecalculationPreviewDto settingsPreview = resultRecalculationService.preview(
                event.getId(), List.of(secondRace.getId()), 0, ADMIN_USERNAME
        );
        resultRecalculationService.apply(event.getId(), settingsPreview.operationId(), ADMIN_USERNAME);
        raceResultsPublicationService.publish(event.getId(), secondRace.getId(), ADMIN_USERNAME);
        var firstRaceProtocol = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "place", "asc"
        );
        var secondRaceProtocol = resultQueryService.search(
                event.getId(), secondRace.getId(), null, null, null, null, null,
                0, 20, "place", "asc"
        );
        assertThat(firstRaceProtocol.content()).allSatisfy(item -> {
            assertThat(item.raceId()).isEqualTo(race.getId());
            assertThat(item.rankingBasis()).isEqualTo("GUN_TIME");
            assertThat(item.place()).isEqualTo(item.overallPlace());
        });
        assertThat(secondRaceProtocol.content()).allSatisfy(item -> {
            assertThat(item.raceId()).isEqualTo(secondRace.getId());
            assertThat(item.rankingBasis()).isEqualTo("GUN_TIME");
            assertThat(item.place()).isEqualTo(item.overallPlace());
        });

        mockMvc.perform(get("/api/events/{eventId}/results", event.getId())
                        .param("raceId", race.getId().toString())
                        .param("rankingBasis", "GUN_TIME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].displayName").value("Ган Лидер"))
                .andExpect(jsonPath("$.content[0].place").value(1))
                .andExpect(jsonPath("$.content[0].rankingBasis").value("GUN_TIME"))
                .andExpect(jsonPath("$.content[0].rankingAchievements[0].place").value(1))
                .andExpect(jsonPath("$.content[0].rankingAchievements[0].type").value("ABSOLUTE"))
                .andExpect(jsonPath("$.content[0].rankingAchievements[0].label").value("1 место · абсолют"))
                .andExpect(jsonPath("$.content[0].rankingAchievements[0].prize").value(true));

        mockMvc.perform(put("/api/admin/races/{raceId}/public-settings", race.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicRankingBasis\":\"GUN_TIME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicRankingBasis").value("GUN_TIME"));
        mockMvc.perform(get("/api/admin/races/{raceId}/public-settings", race.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicRankingBasis").value("GUN_TIME"));
        mockMvc.perform(put("/api/admin/races/{raceId}/public-settings", race.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publicRankingBasis\":null}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId())
                        .param("raceId", race.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].displayName").value("Ган Лидер"))
                .andExpect(jsonPath("$.content[0].rankingBasis").value("GUN_TIME"));

        assertThat(awardPolicyService.get(race.getId()).rankingBasis()).isEqualTo(RankingBasis.GUN_TIME);
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.RACE, race.getId()
        )).extracting(AdminChangeLog::getFieldName)
                .containsOnly("resultsPublicationStatus");
    }

    @Test
    void publicChipTimeSortKeepsFinishedAheadOfFasterDisqualifiedResult() throws Exception {
        Event event = createPublishedEvent("Finished-first sorting", "finished-first-sorting");
        assertThat(importPublishedFixture(
                event.getId(), "finished-first.csv", finishedFirstCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();

        var results = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "chipTime", "asc"
        );

        assertThat(results.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("B", "C", "A");
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId())
                        .param("raceId", race.getId().toString())
                        .param("sort", "chipTime")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].displayName").value("B"))
                .andExpect(jsonPath("$.content[1].displayName").value("C"))
                .andExpect(jsonPath("$.content[2].displayName").value("A"));

        for (RankingBasis basis : List.of(RankingBasis.GUN_TIME, RankingBasis.CHIP_TIME)) {
            awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                    basis, PrimaryStandingMode.ALL, 1, false,
                    AgeCalculationMode.EVENT_DATE, 0, false
            ), ADMIN_USERNAME);
            var official = resultQueryService.search(
                    event.getId(), race.getId(), null, null, null, null, null,
                    0, 20, basis == RankingBasis.GUN_TIME ? "gunTime" : "chipTime", "asc"
            );
            assertThat(official.content()).extracting(ResultListItemDto::displayName)
                    .containsExactly("B", "C", "A");
            assertThat(achievement(official.content(), "B", "ABSOLUTE").place()).isEqualTo(1);
            assertThat(achievement(official.content(), "C", "ABSOLUTE").place()).isEqualTo(2);
            assertThat(item(official.content(), "A").rankingAchievements()).isEmpty();
        }
    }

    @Test
    void publicSortMatrixUsesFinishedFirstBeforePaginationAndNumbersOnlyFinishedInNoneMode() {
        Event event = createPublishedEvent("Finished-first matrix", "finished-first-matrix");
        assertThat(importPublishedFixture(
                event.getId(), "finished-first-matrix.csv",
                finishedFirstMatrixCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.NONE, PrimaryStandingMode.NONE, 0, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);

        Map<String, List<String>> expected = Map.ofEntries(
                Map.entry("gunTime:asc", List.of("Bravo", "Charlie", "Alpha", "Aaron", "Zulu", "NoTime")),
                Map.entry("gunTime:desc", List.of("Alpha", "Charlie", "Bravo", "Zulu", "Aaron", "NoTime")),
                Map.entry("chipTime:asc", List.of("Bravo", "Charlie", "Alpha", "Aaron", "Zulu", "NoTime")),
                Map.entry("chipTime:desc", List.of("Charlie", "Bravo", "Alpha", "Zulu", "Aaron", "NoTime")),
                Map.entry("displayName:asc", List.of("Alpha", "Bravo", "Charlie", "Aaron", "NoTime", "Zulu")),
                Map.entry("displayName:desc", List.of("Charlie", "Bravo", "Alpha", "Zulu", "NoTime", "Aaron")),
                Map.entry("bib:asc", List.of("Charlie", "Bravo", "Alpha", "Aaron", "NoTime", "Zulu")),
                Map.entry("bib:desc", List.of("Alpha", "Bravo", "Charlie", "Zulu", "NoTime", "Aaron"))
        );
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            String[] order = entry.getKey().split(":");
            var page = resultQueryService.search(
                    event.getId(), race.getId(), null, null, null, null, null,
                    0, 20, order[0], order[1]
            );
            assertThat(page.content()).extracting(ResultListItemDto::displayName)
                    .as("public %s %s", order[0], order[1])
                    .containsExactlyElementsOf(entry.getValue());
            assertThat(page.content()).extracting(ResultListItemDto::status)
                    .containsExactly("finished", "finished", "finished",
                            "disqualified", "disqualified", "disqualified");
        }

        var disqualifiedOnly = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, "disqualified",
                0, 20, "gunTime", "desc"
        );
        assertThat(disqualifiedOnly.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("Zulu", "Aaron", "NoTime");
        assertThat(disqualifiedOnly.content()).extracting(ResultListItemDto::displayPosition)
                .containsOnlyNulls();
        var finishedOnly = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, "finished",
                0, 20, "displayName", "desc"
        );
        assertThat(finishedOnly.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("Charlie", "Bravo", "Alpha");
        assertThat(finishedOnly.content()).extracting(ResultListItemDto::displayPosition)
                .containsExactly(1, 2, 3);

        var firstPage = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 2, "chipTime", "asc"
        );
        var secondPage = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                1, 2, "chipTime", "asc"
        );
        var thirdPage = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                2, 2, "chipTime", "asc"
        );
        assertThat(firstPage.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("Bravo", "Charlie");
        assertThat(firstPage.content()).extracting(ResultListItemDto::displayPosition)
                .containsExactly(1, 2);
        assertThat(secondPage.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("Alpha", "Aaron");
        assertThat(secondPage.content()).extracting(ResultListItemDto::displayPosition)
                .containsExactly(3, null);
        assertThat(thirdPage.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("Zulu", "NoTime");
        assertThat(thirdPage.content()).extracting(ResultListItemDto::displayPosition)
                .containsOnlyNulls();
    }

    @Test
    void bibSortUsesNumericValueInBothDirectionsAndKeepsMixedValuesSafe() {
        Event event = createPublishedEvent("Natural bib ordering", "natural-bib-ordering");
        assertThat(importPublishedFixture(
                event.getId(), "natural-bib-ordering.csv", bibSortingCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();

        var ascending = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, "finished",
                0, 50, "bib", "asc"
        );
        assertThat(ascending.content()).extracting(ResultListItemDto::bib).containsExactly(
                "001", "01", "1", "2", "9", "10", "20", "45", "100", "445", "446", "449", "450",
                "A10", "A2", "VIP-1", null
        );
        assertThat(ascending.content()).extracting(ResultListItemDto::bib)
                .filteredOn(bib -> bib != null && bib.matches("(?:9|10|45|100|445|446|449|450)"))
                .containsExactly("9", "10", "45", "100", "445", "446", "449", "450");
        assertThat(ascending.content()).extracting(ResultListItemDto::bib)
                .filteredOn(bib -> bib != null && bib.matches("(?:45|449|450)"))
                .containsExactly("45", "449", "450");

        var descending = resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, "finished",
                0, 50, "bib", "desc"
        );
        assertThat(descending.content()).extracting(ResultListItemDto::bib).containsExactly(
                "450", "449", "446", "445", "100", "45", "20", "10", "9", "2", "1", "01", "001",
                "VIP-1", "A2", "A10", null
        );
    }

    @Test
    void bibNumericOrderingIsStableAcrossPaginationAndDoesNotChangeOfficialAchievements() {
        Event event = createPublishedEvent("Paged bib ordering", "paged-bib-ordering");
        assertThat(importPublishedFixture(
                event.getId(), "paged-bib-ordering.csv", bibSortingCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 3, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);

        Map<Long, List<RankingAchievementDto>> achievementsByTime = achievementMap(
                event.getId(), race.getId(), "gunTime", "asc"
        );
        List<String> bibsAcrossPages = new ArrayList<>();
        for (int page = 0; page < 5; page++) {
            bibsAcrossPages.addAll(resultQueryService.search(
                    event.getId(), race.getId(), "Runner", null, "male", null, "finished",
                    page, 4, "bib", "asc"
            ).content().stream().map(ResultListItemDto::bib).toList());
        }
        assertThat(bibsAcrossPages).containsExactly(
                "001", "01", "1", "2", "9", "10", "20", "45", "100", "445", "446", "449", "450",
                "A10", "A2", "VIP-1", null
        );
        assertThat(bibsAcrossPages.subList(8, 12)).containsExactly("100", "445", "446", "449");
        assertThat(achievementMap(event.getId(), race.getId(), "bib", "asc"))
                .containsExactlyInAnyOrderEntriesOf(achievementsByTime);
        assertThat(achievementMap(event.getId(), race.getId(), "bib", "desc"))
                .containsExactlyInAnyOrderEntriesOf(achievementsByTime);
    }

    @Test
    void syntheticM52OfficialAchievementsStayAttachedToResultIdentityAcrossEveryPublicOrder() throws Exception {
        Event event = createPublishedEvent("M52 stable achievements", "m52-stable-achievements");
        assertThat(importPublishedFixture(
                event.getId(), "results_m52_2025.csv", Files.readAllBytes(sample("results_m52_2025.csv"))
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "42.2 km").orElseThrow();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.BY_GENDER, 3, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);
        race.setPublicRankingBasis(RankingBasis.GUN_TIME);
        raceRepository.saveAndFlush(race);
        assertThat(resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc").content())
                .allSatisfy(item -> assertThat(item.rankingBasis()).isEqualTo("CHIP_TIME"));

        Map<Long, List<RankingAchievementDto>> baseline = achievementMap(event.getId(), race.getId(),
                "chipTime", "asc");
        assertThat(baseline).hasSize(8);
        for (String[] order : List.of(
                new String[]{"gunTime", "asc"},
                new String[]{"gunTime", "desc"},
                new String[]{"chipTime", "desc"},
                new String[]{"displayName", "asc"},
                new String[]{"bib", "asc"}
        )) {
            assertThat(achievementMap(event.getId(), race.getId(), order[0], order[1]))
                    .as("official achievements for %s %s", order[0], order[1])
                    .containsExactlyInAnyOrderEntriesOf(baseline);
        }

        ResultListItemDto target = resultQueryService.search(
                event.getId(), race.getId(), null, null, "female", null, null,
                0, 200, "gunTime", "desc"
        ).content().stream().filter(item -> !item.rankingAchievements().isEmpty()).findFirst().orElseThrow();
        var searched = resultQueryService.search(
                event.getId(), race.getId(), target.displayName(), null, null, null, null,
                0, 20, "bib", "asc"
        );
        assertThat(item(searched.content(), target.displayName()).rankingAchievements())
                .isEqualTo(baseline.get(target.resultId()));
        var byBib = resultQueryService.search(
                event.getId(), race.getId(), null, target.bib(), null, null, null,
                0, 20, "displayName", "desc"
        );
        assertThat(byBib.content()).filteredOn(item -> item.resultId().equals(target.resultId()))
                .singleElement().extracting(ResultListItemDto::rankingAchievements)
                .isEqualTo(baseline.get(target.resultId()));
    }

    @Test
    void noneStandingUsesViewPositionsAndRejectsAwardsOrCategoryConflicts() throws Exception {
        Event event = createPublishedEvent("Гонка Героев массовый старт", "hero-mass-none");
        assertThat(importPublishedFixture(
                event.getId(), "none-standing.csv", noneStandingCsv().getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "Массовый старт").orElseThrow();

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 0, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);
        assertThat(resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "chipTime", "asc").content())
                .allSatisfy(item -> {
                    assertThat(item.rankingAchievements()).isNotEmpty();
                    assertThat(item.rankingAchievements()).allMatch(achievement -> !achievement.prize());
                });
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.CHIP_TIME, PrimaryStandingMode.ALL, 0, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);
        assertThat(resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc").content())
                .allSatisfy(item -> assertThat(item.rankingAchievements()).isNotEmpty());

        assertThatThrownBy(() -> awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.NONE, PrimaryStandingMode.ALL, 0, false,
                AgeCalculationMode.EVENT_DATE, 0, false), ADMIN_USERNAME))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("NONE");
        assertThatThrownBy(() -> awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.NONE, PrimaryStandingMode.NONE, 3, false,
                AgeCalculationMode.EVENT_DATE, 0, false), ADMIN_USERNAME))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("NONE");
        assertThatThrownBy(() -> awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.NONE, PrimaryStandingMode.NONE, 0, true,
                AgeCalculationMode.EVENT_DATE, 3, false), ADMIN_USERNAME))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("NONE");

        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.NONE, PrimaryStandingMode.NONE, 0, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);
        assertThat(raceRepository.findById(race.getId()).orElseThrow().getPublicRankingBasis())
                .isEqualTo(RankingBasis.NONE);

        var chipPage = resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                0, 2, "chipTime", "asc");
        assertThat(chipPage.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("B", "D");
        assertThat(chipPage.content()).extracting(ResultListItemDto::displayPosition)
                .containsExactly(1, 2);
        assertThat(chipPage.content()).allSatisfy(item -> {
            assertThat(item.rankingBasis()).isEqualTo("NONE");
            assertThat(item.place()).isNull();
            assertThat(item.rankingAchievements()).isEmpty();
        });
        var secondPage = resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                1, 2, "chipTime", "asc");
        assertThat(secondPage.content()).extracting(ResultListItemDto::displayPosition)
                .containsExactly(3, 4);
        var gunOrder = resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc");
        assertThat(gunOrder.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("A", "B", "C", "D", "E", "F");
        assertThat(item(gunOrder.content(), "B").displayPosition()).isEqualTo(2);
        var female = resultQueryService.search(event.getId(), race.getId(), null, null, "female", null, null,
                0, 20, "gunTime", "asc");
        assertThat(female.content()).extracting(ResultListItemDto::displayName)
                .containsExactly("B", "C", "E");
        assertThat(female.content()).extracting(ResultListItemDto::displayPosition)
                .containsExactly(1, 2, 3);
        var descending = resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "chipTime", "desc");
        assertThat(descending.content().getFirst().displayPosition()).isEqualTo(1);
        assertThat(resultQueryService.getPublishedResult(chipPage.content().getFirst().resultId()).rankingBasis())
                .isEqualTo("NONE");
        assertThat(resultQueryService.getPublishedResult(chipPage.content().getFirst().resultId()).rankingAchievements())
                .isEmpty();
    }

    @Test
    void separatesEventAndRaceResultsPublicationAndTreatsLegacyGlobalFlagAsNonBlocking() throws Exception {
        EventSeries series = createSeries("Future Series", "future-series");
        Event event = new Event();
        event.setEventSeries(series);
        event.setName("Future Event");
        event.setSlug("future-event");
        event.setStartsAt(Instant.parse("2027-06-15T06:00:00Z"));
        event.setTimeZone("Europe/Moscow");
        event.setPublicationStatus(EventPublicationStatus.PUBLISHED);
        event.setResultsPublicationStatus(ru.sportsresults.domain.ResultsPublicationStatus.DRAFT);
        event = eventRepository.saveAndFlush(event);
        importPublishedFixture(event.getId(), "future.csv", oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();

        mockMvc.perform(get("/api/events/slug/{slug}", event.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultsPublished").value(true));
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId()).param("raceId", race.getId().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/events/{eventId}/publication", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventPublicationStatus\":\"PUBLISHED\",\"resultsPublicationStatus\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultsPublished").value(true));
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId()).param("raceId", race.getId().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/admin/events/{eventId}/publication", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventPublicationStatus\":\"PUBLISHED\",\"resultsPublicationStatus\":\"DRAFT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/slug/{slug}", event.getSlug())).andExpect(status().isOk());
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId()).param("raceId", race.getId().toString()))
                .andExpect(status().isOk());
        assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.PUBLISHED);

        mockMvc.perform(put("/api/admin/events/{eventId}/publication", event.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventPublicationStatus\":\"DRAFT\",\"resultsPublicationStatus\":\"DRAFT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/events/slug/{slug}", event.getSlug())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/events/{eventId}/results", event.getId()).param("raceId", race.getId().toString()))
                .andExpect(status().isNotFound());
        assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.PUBLISHED);
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.EVENT, event.getId())).extracting(AdminChangeLog::getFieldName)
                .contains("publicationStatus", "resultsPublicationStatus");
    }

    @Test
    void publishesAndDraftsOneRaceWithDurableHistoryWithoutChangingSportsFacts() throws Exception {
        Event event = createPublishedEvent("Race publication", "race-publication");
        assertThat(this.importService.importEvent(
                event.getId(), "race-publication.csv", oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        Registration registration = registrationRepository.findAllByRaceIdAndBib(race.getId(), "A-1").getFirst();
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();
        ResultIssueRequest issue = resultIssue(
                event, registration, result, ResultIssueType.RESULT_CORRECTION,
                ResultCorrectionReason.OFFICIAL_TIME, result.getStatus()
        );
        Map<String, Object> issueSnapshot = resultIssueSnapshotState(issue.getId());
        long revision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        long registrations = registrationRepository.countByRaceEventId(event.getId());
        long results = resultRepository.countByRegistrationRaceEventId(event.getId());
        Map<String, Object> registrationBefore = jdbcTemplate.queryForMap(
                "SELECT * FROM registrations WHERE id=?", registration.getId());
        Map<String, Object> resultBefore = jdbcTemplate.queryForMap(
                "SELECT * FROM results WHERE id=?", result.getId());

        assertThat(race.getResultsPublicationStatus()).isEqualTo(ResultsPublicationStatus.DRAFT);
        assertThatThrownBy(() -> resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc"
        )).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> resultQueryService.getPublishedResult(result.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(resultQueryService.getAdminResult(result.getId()).resultId()).isEqualTo(result.getId());
        assertThat(resultInquiryService.lookup(event.getId(), "A-1").lookupState())
                .isEqualTo(ResultInquiryLookupState.NOT_FOUND);
        mockMvc.perform(get("/api/events/slug/{slug}", event.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.races[0].resultsPublicationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.races[0].resultsPublished").value(false));

        mockMvc.perform(post("/api/admin/events/{eventId}/races/{raceId}/results/publish",
                        event.getId(), race.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/events/{eventId}/races/{raceId}/results/publish",
                        event.getId() + 1000, race.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/events/{eventId}/races/{raceId}/results/publish",
                        event.getId(), race.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("DRAFT"))
                .andExpect(jsonPath("$.currentStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.changedAt").exists());

        assertThat(resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc"
        ).content()).singleElement();
        assertThat(resultQueryService.getPublishedResult(result.getId()).resultId()).isEqualTo(result.getId());
        assertThat(resultInquiryService.lookup(event.getId(), "A-1").lookupState())
                .isEqualTo(ResultInquiryLookupState.RESULT_PUBLIC);
        assertThat(raceResultsPublicationService.publish(event.getId(), race.getId(), ADMIN_USERNAME).changedAt())
                .isNull();
        assertThat(raceResultPublicationHistoryRepository.findAllByRaceIdOrderByCreatedAtAscIdAsc(race.getId()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getFromStatus()).isEqualTo(ResultsPublicationStatus.DRAFT);
                    assertThat(history.getToStatus()).isEqualTo(ResultsPublicationStatus.PUBLISHED);
                    assertThat(history.getReason()).isNull();
                    assertThat(history.getActor()).isEqualTo(ADMIN_USERNAME);
                });

        mockMvc.perform(post("/api/admin/events/{eventId}/races/{raceId}/results/draft",
                        event.getId(), race.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  Исправление протокола  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousStatus").value("PUBLISHED"))
                .andExpect(jsonPath("$.currentStatus").value("DRAFT"));
        assertThat(raceResultsPublicationService.draft(event.getId(), race.getId(), "ignored", ADMIN_USERNAME).changedAt())
                .isNull();

        List<RaceResultPublicationHistory> history =
                raceResultPublicationHistoryRepository.findAllByRaceIdOrderByCreatedAtAscIdAsc(race.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(1).getFromStatus()).isEqualTo(ResultsPublicationStatus.PUBLISHED);
        assertThat(history.get(1).getToStatus()).isEqualTo(ResultsPublicationStatus.DRAFT);
        assertThat(history.get(1).getReason()).isEqualTo("Исправление протокола");
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.RACE, race.getId())).filteredOn(log ->
                log.getFieldName().equals("resultsPublicationStatus")).hasSize(2);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revision);
        assertThat(registrationRepository.countByRaceEventId(event.getId())).isEqualTo(registrations);
        assertThat(resultRepository.countByRegistrationRaceEventId(event.getId())).isEqualTo(results);
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM registrations WHERE id=?", registration.getId()))
                .isEqualTo(registrationBefore);
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM results WHERE id=?", result.getId()))
                .isEqualTo(resultBefore);
        assertThat(resultIssueRequestRepository.findById(issue.getId()).orElseThrow()).satisfies(saved -> {
            assertThat(saved.getRegistration().getId()).isEqualTo(registration.getId());
            assertThat(saved.getResult().getId()).isEqualTo(result.getId());
            assertThat(saved.getStatus()).isEqualTo(ResultIssueStatus.NEW);
            assertThat(saved.getQueueArchivedAt()).isNull();
        });
        assertThat(resultIssueSnapshotState(issue.getId())).isEqualTo(issueSnapshot);
        assertThatThrownBy(() -> resultQueryService.getPublishedResult(result.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(resultQueryService.getAdminResult(result.getId()).resultId()).isEqualTo(result.getId());
    }

    @Test
    void isolatesMixedRacePublicationAndKeepsDraftDuplicatesPrivateBehindTheEventGate() throws Exception {
        Event event = createPublishedEvent("Mixed publication", "mixed-publication");
        event.setStartsAt(Instant.parse("2027-06-15T06:00:00Z"));
        eventRepository.saveAndFlush(event);
        byte[] csv = (csvHeader() + """
                Анна,Первая,female,1990-01-01,5 km,SAME,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Алла,Отдельная,female,1993-03-03,5 km,A-ONLY,Open,finished,1100.0,1000.0,2.0,2.0,2.0,2.0,2.0,2.0
                Борис,Второй,male,1991-01-01,10 km,SAME,Open,finished,2000.0,1900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """).getBytes(StandardCharsets.UTF_8);
        assertThat(this.importService.importEvent(event.getId(), "mixed.csv", csv).status())
                .isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race publishedRace = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        Race draftRace = raceRepository.findByEventIdAndSourceCode(event.getId(), "10 km").orElseThrow();
        raceResultsPublicationService.publish(event.getId(), publishedRace.getId(), ADMIN_USERNAME);

        assertThat(resultQueryService.search(
                event.getId(), publishedRace.getId(), null, "SAME", null, null, null,
                0, 20, "gunTime", "asc").content()).singleElement()
                .extracting(ResultListItemDto::displayName).isEqualTo("Анна Первая");
        assertThatThrownBy(() -> resultQueryService.search(
                event.getId(), draftRace.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc")).isInstanceOf(ResourceNotFoundException.class);
        assertThat(resultQueryService.searchAdmin(
                event.getId(), draftRace.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc", RankingBasis.GUN_TIME
        ).content()).singleElement().extracting(ResultListItemDto::displayName).isEqualTo("Борис Второй");
        assertThat(resultInquiryService.lookup(event.getId(), "SAME").lookupState())
                .isEqualTo(ResultInquiryLookupState.RESULT_PUBLIC);
        mockMvc.perform(get("/api/events/slug/{slug}", event.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.races.length()").value(2))
                .andExpect(jsonPath("$.races[0].resultsPublished").value(true))
                .andExpect(jsonPath("$.races[1].resultsPublished").value(false));

        raceResultsPublicationService.draft(event.getId(), publishedRace.getId(), "E2E correction", ADMIN_USERNAME);
        assertThatThrownBy(() -> resultQueryService.search(
                event.getId(), publishedRace.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> resultQueryService.search(
                event.getId(), draftRace.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc")).isInstanceOf(ResourceNotFoundException.class);
        raceResultsPublicationService.publish(event.getId(), publishedRace.getId(), ADMIN_USERNAME);

        byte[] updatePublishedRace = (csvHeader() + """
                Алла,Обновлённая,female,1993-03-03,5 km,A-ONLY,Open,finished,1500.0,1400.0,2.0,2.0,2.0,2.0,2.0,2.0
                """).getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto updatePreview = importPreviewService.preview(
                event.getId(), "update-published-a.csv", updatePublishedRace,
                ImportOperationMode.UPDATE_EXISTING, List.of(publishedRace.getId()), 100, ADMIN_USERNAME);
        importApplyService.apply(event.getId(), updatePreview.operationId(), updatePublishedRace, ADMIN_USERNAME);
        assertThat(raceRepository.findById(publishedRace.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.PUBLISHED);

        byte[] addToDraftRace = (csvHeader() + """
                Вера,Новая,female,1992-02-02,10 km,B-NEW,Open,finished,2500.0,2400.0,2.0,1.0,1.0,2.0,1.0,1.0
                """).getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto addPreview = importPreviewService.preview(
                event.getId(), "add-draft-b.csv", addToDraftRace,
                ImportOperationMode.ADD_NEW, List.of(draftRace.getId()), 100, ADMIN_USERNAME);
        importApplyService.apply(event.getId(), addPreview.operationId(), addToDraftRace, ADMIN_USERNAME);
        assertThat(raceRepository.findById(draftRace.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.DRAFT);

        eventService.updatePublication(event.getId(), new ru.sportsresults.api.dto.UpdateEventPublicationRequest(
                EventPublicationStatus.PUBLISHED, ResultsPublicationStatus.DRAFT), ADMIN_USERNAME);
        assertThat(resultQueryService.search(
                event.getId(), publishedRace.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc").content()).hasSize(2);
        assertThat(raceRepository.findById(publishedRace.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.PUBLISHED);
        assertThat(raceRepository.findById(draftRace.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.DRAFT);

        eventService.updatePublication(event.getId(), new ru.sportsresults.api.dto.UpdateEventPublicationRequest(
                EventPublicationStatus.PUBLISHED, ResultsPublicationStatus.PUBLISHED), ADMIN_USERNAME);
        assertThat(resultQueryService.search(
                event.getId(), publishedRace.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc").content()).hasSize(2);
        assertThatThrownBy(() -> resultQueryService.search(
                event.getId(), draftRace.getId(), null, null, null, null, null,
                0, 20, "gunTime", "asc")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void addAndUpdateImportsNeverChangeDraftOrPublishedRaceState() {
        Event event = createPublishedEvent("Import publication invariant", "import-publication-invariant");
        byte[] initial = oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8);
        assertThat(this.importService.importEvent(event.getId(), "initial.csv", initial).status())
                .isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        assertThat(race.getResultsPublicationStatus()).isEqualTo(ResultsPublicationStatus.DRAFT);

        byte[] draftAdd = singleNewCsv("DRAFT-ADD", "1200.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto addPreview = importPreviewService.preview(
                event.getId(), "draft-add.csv", draftAdd, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME);
        importApplyService.apply(event.getId(), addPreview.operationId(), draftAdd, ADMIN_USERNAME);
        assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.DRAFT);

        byte[] draftUpdate = oneRowCsv("1800.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto updatePreview = importPreviewService.preview(
                event.getId(), "draft-update.csv", draftUpdate, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME);
        importApplyService.apply(event.getId(), updatePreview.operationId(), draftUpdate, ADMIN_USERNAME);
        assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.DRAFT);

        raceResultsPublicationService.publish(event.getId(), race.getId(), ADMIN_USERNAME);
        byte[] publishedAdd = singleNewCsv("PUBLISHED-ADD", "1300.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto publishedAddPreview = importPreviewService.preview(
                event.getId(), "published-add.csv", publishedAdd, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME);
        importApplyService.apply(event.getId(), publishedAddPreview.operationId(), publishedAdd, ADMIN_USERNAME);
        assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.PUBLISHED);

        byte[] publishedUpdate = oneRowCsv("1900.0").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto publishedUpdatePreview = importPreviewService.preview(
                event.getId(), "published-update.csv", publishedUpdate, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME);
        importApplyService.apply(
                event.getId(), publishedUpdatePreview.operationId(), publishedUpdate, ADMIN_USERNAME);
        assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.PUBLISHED);
        assertThat(raceResultPublicationHistoryRepository.findAllByRaceIdOrderByCreatedAtAscIdAsc(race.getId()))
                .hasSize(1);
    }

    @Test
    void concurrentPublishOfOneDraftRaceCreatesOneTransition() throws Exception {
        Event event = createPublishedEvent("Concurrent race publish", "concurrent-race-publish");
        this.importService.importEvent(
                event.getId(), "concurrent.csv", oneRowCsv("1000.0").getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ru.sportsresults.api.dto.RaceResultsPublicationDto> publish = () -> {
            ready.countDown();
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            return raceResultsPublicationService.publish(event.getId(), race.getId(), ADMIN_USERNAME);
        };

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(publish);
            var second = executor.submit(publish);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .extracting(response -> response.currentStatus())
                    .containsOnly(ResultsPublicationStatus.PUBLISHED);
        }

        assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.PUBLISHED);
        assertThat(raceResultPublicationHistoryRepository.findAllByRaceIdOrderByCreatedAtAscIdAsc(race.getId()))
                .singleElement();
    }

    @Test
    void createsDraftEventByDefaultAndValidatesIanaTimeZone() {
        EventSeries series = createSeries("Draft Series", "draft-series");
        var created = eventService.createEvent(new ru.sportsresults.api.dto.CreateEventRequest(
                series.getId(), "Draft Event", "draft-event", null, null, "Москва",
                "Asia/Yekaterinburg", null));
        assertThat(created.publicationStatus()).isEqualTo(EventPublicationStatus.DRAFT);
        assertThat(created.resultsPublicationStatus()).isEqualTo(ru.sportsresults.domain.ResultsPublicationStatus.DRAFT);
        assertThat(created.timeZone()).isEqualTo("Asia/Yekaterinburg");

        assertThatThrownBy(() -> eventService.createEvent(new ru.sportsresults.api.dto.CreateEventRequest(
                series.getId(), "Bad Zone", "bad-zone", null, null, null, "UTC+05:00", null)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("IANA");
    }

    @Test
    void bulkEventEndpointsPreviewAndAtomicallyCreateOrdinaryDraftEvents() throws Exception {
        EventSeries template = createSeries("Гонка Героев", "bulk-heroes");
        String command = """
                {
                  "eventSeriesId": %d,
                  "date": "2027-08-15",
                  "events": [
                    {"name":"Казань","location":"Казань","timeZone":"Europe/Moscow"},
                    {"name":"Екатеринбург","location":"Екатеринбург","timeZone":"Asia/Yekaterinburg"}
                  ]
                }
                """.formatted(template.getId());

        mockMvc.perform(post("/api/admin/events/bulk/preview")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(command))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateName").value("Гонка Героев"))
                .andExpect(jsonPath("$.requestedCount").value(2))
                .andExpect(jsonPath("$.creatableCount").value(2))
                .andExpect(jsonPath("$.events[0].creatable").value(true));
        assertThat(eventRepository.countByEventSeriesId(template.getId())).isZero();

        MvcResult result = mockMvc.perform(post("/api/admin/events/bulk")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(command))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].eventSeriesId").value(template.getId()))
                .andExpect(jsonPath("$.events[0].publicationStatus").value("DRAFT"))
                .andReturn();

        var eventNodes = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("events");
        List<Long> ids = new ArrayList<>();
        eventNodes.forEach(node -> ids.add(node.get("id").asLong()));
        assertThat(ids).hasSize(2).doesNotHaveDuplicates();
        List<Event> createdEvents = eventRepository.findAllByEventSeriesIdOrderByIdAsc(template.getId());
        assertThat(createdEvents)
                .extracting(Event::getName)
                .containsExactly("Казань", "Екатеринбург");
        assertThat(createdEvents).extracting(Event::getStartsAt)
                .containsExactly(Instant.parse("2027-08-14T21:00:00Z"), Instant.parse("2027-08-14T19:00:00Z"));
        assertThat(eventSeriesRepository.count()).isEqualTo(1);
    }

    @Test
    void bulkPreviewMarksExistingAndRequestDuplicatesAndApplyRejectsThem() throws Exception {
        EventSeries template = createSeries("Повтор", "bulk-duplicate");
        createEvent(template, "Москва", "bulk-existing-moscow", "Москва", "2027-08-14T21:00:00Z");
        String existingCommand = """
                {"eventSeriesId":%d,"date":"2027-08-15","events":[
                  {"name":"Москва","location":"Москва","timeZone":"Europe/Moscow"},
                  {"name":"Казань","location":"Казань","timeZone":"Europe/Moscow"}
                ]}
                """.formatted(template.getId());

        mockMvc.perform(post("/api/admin/events/bulk/preview")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(existingCommand))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatableCount").value(1))
                .andExpect(jsonPath("$.events[0].creatable").value(false))
                .andExpect(jsonPath("$.events[0].existingEventId").isNumber())
                .andExpect(jsonPath("$.events[1].creatable").value(true));
        mockMvc.perform(post("/api/admin/events/bulk")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(existingCommand))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BULK_EVENT_DUPLICATE"));

        String repeatedCommand = """
                {"eventSeriesId":%d,"date":"2027-09-01","events":[
                  {"name":"Омск","location":"Омск","timeZone":"Asia/Omsk"},
                  {"name":"Омск","location":"  омск  ","timeZone":"Asia/Omsk"}
                ]}
                """.formatted(template.getId());
        mockMvc.perform(post("/api/admin/events/bulk/preview")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(repeatedCommand))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creatableCount").value(0))
                .andExpect(jsonPath("$.events[0].duplicateInRequest").value(true))
                .andExpect(jsonPath("$.events[1].duplicateInRequest").value(true));
        assertThat(eventRepository.countByEventSeriesId(template.getId())).isEqualTo(1);
    }

    @Test
    void bulkCreationValidatesEveryTimeZoneBeforeWritingAndLeavesPublishedEventsUntouched() throws Exception {
        EventSeries template = createSeries("Atomic", "bulk-atomic");
        Event published = createEvent(template, "Опубликовано", "bulk-published", "Москва", "2027-06-15T06:00:00Z");
        long publishedId = published.getId();
        String command = """
                {"eventSeriesId":%d,"date":"2027-08-15","events":[
                  {"name":"Казань","location":"Казань","timeZone":"Europe/Moscow"},
                  {"name":"Ошибка","location":"Ошибка","timeZone":"UTC+05:00"}
                ]}
                """.formatted(template.getId());

        mockMvc.perform(post("/api/admin/events/bulk")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(command))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));

        assertThat(eventRepository.countByEventSeriesId(template.getId())).isEqualTo(1);
        Event unchanged = eventRepository.findById(publishedId).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Опубликовано");
        assertThat(unchanged.getPublicationStatus()).isEqualTo(EventPublicationStatus.PUBLISHED);
    }

    @Test
    void templateListIncludesEventCountAndRenameKeepsSlugAndExistingEventsIndependent() throws Exception {
        EventSeries template = createSeries("Старое имя", "stable-template-url");
        Event event = createEvent(template, "Казань", "independent-event", "Казань", "2027-08-15T06:00:00Z");

        mockMvc.perform(get("/api/admin/event-series").with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventCount").value(1))
                .andExpect(jsonPath("$[0].startCount").value(0));
        mockMvc.perform(put("/api/admin/event-series/{id}", template.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Новое имя","description":null,"active":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Новое имя"))
                .andExpect(jsonPath("$.slug").value("stable-template-url"));

        Event unchanged = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Казань");
        assertThat(unchanged.getSlug()).isEqualTo("independent-event");
    }

    @Test
    void managesOrderedTemplateStartsAndOptionalAwardPolicyWithoutNumericOrderInput() throws Exception {
        EventSeries template = createSeries("Template starts", "template-starts");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_series_start_templates WHERE event_series_id=?",
                Long.class, template.getId())).isZero();

        long five = createTemplateStart(template.getId(), "5 км", 5000);
        long ten = createTemplateStart(template.getId(), "10 км", 10000);
        long championship = createTemplateStart(template.getId(), "Чемпионат", null);

        mockMvc.perform(get("/api/admin/event-series/{id}/start-templates", template.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(five))
                .andExpect(jsonPath("$[0].displayOrder").value(0))
                .andExpect(jsonPath("$[2].id").value(championship))
                .andExpect(jsonPath("$[0].sourceCode").doesNotExist())
                .andExpect(jsonPath("$[2].awardPolicy").doesNotExist());
        mockMvc.perform(get("/api/admin/event-series")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].startCount").value(3));

        mockMvc.perform(put("/api/admin/event-series/{seriesId}/start-templates/{startId}",
                        template.getId(), five)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"5 км новый","distanceMeters":5100,"publicVisible":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("5 км новый"))
                .andExpect(jsonPath("$.publicVisible").value(false));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='event_series_start_templates'
                  AND column_name='source_code'
                """, Long.class)).isZero();

        String policy = """
                {"rankingBasis":"GUN_TIME","primaryStandingMode":"BY_GENDER",
                 "absolutePrizePlaces":3,"categoryEnabled":true,"ageCalculationMode":"EVENT_DATE",
                 "categoryPrizePlaces":2,"excludeAbsoluteWinnersFromCategory":true}
                """;
        mockMvc.perform(put("/api/admin/event-series/{seriesId}/start-templates/{startId}/award-policy",
                        template.getId(), five)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(policy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankingBasis").value("GUN_TIME"));
        mockMvc.perform(get("/api/admin/event-series/{seriesId}/start-templates/{startId}/award-policy",
                        template.getId(), five).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryEnabled").value(true));

        mockMvc.perform(put("/api/admin/event-series/{seriesId}/start-templates/reorder", template.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateStartIds\":[%d,%d,%d]}".formatted(championship, ten, five)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(championship))
                .andExpect(jsonPath("$[2].id").value(five));
        mockMvc.perform(put("/api/admin/event-series/{seriesId}/start-templates/reorder", template.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateStartIds\":[%d,%d,%d]}".formatted(championship, championship, five)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TEMPLATE_START_ORDER"));

        mockMvc.perform(delete("/api/admin/event-series/{seriesId}/start-templates/{startId}/award-policy",
                        template.getId(), five).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/admin/event-series/{seriesId}/start-templates/{startId}",
                        template.getId(), ten).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_series_start_templates WHERE event_series_id=?",
                Long.class, template.getId())).isEqualTo(2);
    }

    @Test
    void supportsAllAbsoluteAndCategoryAwardCombinationsWithoutChangingRankingSemantics() throws Exception {
        EventSeries template = createSeries("Award combinations", "award-combinations");
        long start = createTemplateStart(template.getId(), "10 км", 10000);
        String[] policies = {
                """
                {"rankingBasis":"GUN_TIME","primaryStandingMode":"BY_GENDER",
                 "absolutePrizePlaces":3,"categoryEnabled":false,"ageCalculationMode":"EVENT_DATE",
                 "categoryPrizePlaces":0,"excludeAbsoluteWinnersFromCategory":false}
                """,
                """
                {"rankingBasis":"CHIP_TIME","primaryStandingMode":"NONE",
                 "absolutePrizePlaces":0,"categoryEnabled":true,"ageCalculationMode":"END_OF_EVENT_YEAR",
                 "categoryPrizePlaces":3,"excludeAbsoluteWinnersFromCategory":false}
                """,
                """
                {"rankingBasis":"GUN_TIME","primaryStandingMode":"ALL",
                 "absolutePrizePlaces":3,"categoryEnabled":true,"ageCalculationMode":"EVENT_DATE",
                 "categoryPrizePlaces":2,"excludeAbsoluteWinnersFromCategory":true}
                """,
                """
                {"rankingBasis":"CHIP_TIME","primaryStandingMode":"NONE",
                 "absolutePrizePlaces":0,"categoryEnabled":false,"ageCalculationMode":"EVENT_DATE",
                 "categoryPrizePlaces":0,"excludeAbsoluteWinnersFromCategory":false}
                """
        };
        for (String policy : policies) {
            mockMvc.perform(put("/api/admin/event-series/{seriesId}/start-templates/{startId}/award-policy",
                            template.getId(), start)
                            .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(policy))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/admin/event-series/{seriesId}/start-templates/{startId}/award-policy",
                        template.getId(), start).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rankingBasis").value("CHIP_TIME"))
                .andExpect(jsonPath("$.primaryStandingMode").value("NONE"))
                .andExpect(jsonPath("$.categoryEnabled").value(false));
    }

    @Test
    void templateCategoriesAreValidatedCopiedAndIndependentFromMaterializedRaceCategories() throws Exception {
        EventSeries template = createSeries("Category template", "category-template");
        long start = createTemplateStart(template.getId(), "10 км", 10000);
        mockMvc.perform(put("/api/admin/event-series/{seriesId}/start-templates/{startId}/award-policy",
                        template.getId(), start)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rankingBasis":"CHIP_TIME","primaryStandingMode":"NONE",
                                 "absolutePrizePlaces":0,"categoryEnabled":true,
                                 "ageCalculationMode":"END_OF_EVENT_YEAR","categoryPrizePlaces":3,
                                 "excludeAbsoluteWinnersFromCategory":false}
                                """))
                .andExpect(status().isOk());

        MvcResult firstCategory = mockMvc.perform(post(
                        "/api/admin/event-series/{seriesId}/start-templates/{startId}/categories",
                        template.getId(), start)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceName":"18-29","displayName":"18–29","minAge":18,"maxAge":29,
                                 "gender":null,"displayOrder":0,"enabled":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateStartId").value(start))
                .andReturn();
        long firstTemplateCategoryId = objectMapper.readTree(
                firstCategory.getResponse().getContentAsByteArray()).get("id").asLong();
        MvcResult secondCategory = mockMvc.perform(post(
                        "/api/admin/event-series/{seriesId}/start-templates/{startId}/categories",
                        template.getId(), start)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceName":"30-39","displayName":"30–39","minAge":30,"maxAge":39,
                                 "gender":null,"displayOrder":1,"enabled":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long secondTemplateCategoryId = objectMapper.readTree(
                secondCategory.getResponse().getContentAsByteArray()).get("id").asLong();

        mockMvc.perform(post("/api/admin/event-series/{seriesId}/start-templates/{startId}/categories",
                        template.getId(), start)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceName":"overlap","displayName":"Overlap","minAge":25,"maxAge":35,
                                 "gender":null,"displayOrder":2,"enabled":true}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_RANGE_OVERLAP"));
        mockMvc.perform(get("/api/admin/event-series/{seriesId}/start-templates", template.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categories.length()").value(2))
                .andExpect(jsonPath("$[0].categories[0].displayName").value("18–29"));

        String command = """
                {"event":{"eventSeriesId":%d,"name":"Казань","startsAt":"2027-08-15T06:00:00Z",
                  "location":"Казань","timeZone":"Europe/Moscow","publicationStatus":"DRAFT"},
                 "starts":[{"templateStartId":%d,"name":"10 км","distanceMeters":10000,
                    "sourceCode":null,"publicVisible":true,"awardPolicy":null}]}
                """.formatted(template.getId(), start);
        MvcResult created = mockMvc.perform(post("/api/admin/events/with-starts")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(command))
                .andExpect(status().isCreated())
                .andReturn();
        long eventId = objectMapper.readTree(created.getResponse().getContentAsByteArray()).get("event").get("id").asLong();
        Race race = raceRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId).getFirst();
        List<Category> copied = categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(race.getId());
        assertThat(copied).extracting(Category::getDisplayName).containsExactly("18–29", "30–39");
        assertThat(copied).allSatisfy(category -> {
            assertThat(category.getId()).isNotNull();
            assertThat(category.getRace().getId()).isEqualTo(race.getId());
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT age_calculation_mode FROM award_policies WHERE race_id=?", String.class, race.getId()))
                .isEqualTo("END_OF_EVENT_YEAR");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT primary_standing_mode FROM award_policies WHERE race_id=?", String.class, race.getId()))
                .isEqualTo("NONE");

        mockMvc.perform(put(
                        "/api/admin/event-series/{seriesId}/start-templates/{startId}/categories/{categoryId}",
                        template.getId(), start, firstTemplateCategoryId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceName":"18-29","displayName":"Шаблон 18–29","minAge":18,"maxAge":29,
                                 "gender":null,"displayOrder":0,"enabled":true}
                                """))
                .andExpect(status().isOk());
        assertThat(categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(race.getId()))
                .extracting(Category::getDisplayName).containsExactly("18–29", "30–39");

        Category realFirst = categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(race.getId()).getFirst();
        mockMvc.perform(put("/api/admin/races/{raceId}/categories/{categoryId}", race.getId(), realFirst.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceName":"18-29","displayName":"Event 18–29","minAge":18,"maxAge":29,
                                 "gender":null,"displayOrder":0,"enabled":true}
                                """))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT display_name FROM event_series_start_category_templates WHERE id=?",
                String.class, firstTemplateCategoryId)).isEqualTo("Шаблон 18–29");

        mockMvc.perform(delete(
                        "/api/admin/event-series/{seriesId}/start-templates/{startId}/categories/{categoryId}",
                        template.getId(), start, secondTemplateCategoryId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());
        assertThat(categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(race.getId()))
                .extracting(Category::getDisplayName).containsExactly("Event 18–29", "30–39");
    }

    @Test
    void singleEventCopyIsTransactionalEditableAndIndependentFromItsTemplate() throws Exception {
        EventSeries template = createSeries("Copy template", "copy-template");
        long five = createTemplateStart(template.getId(), "5 км", 5000);
        long excluded = createTemplateStart(template.getId(), "Чемпионат", null);
        mockMvc.perform(put("/api/admin/event-series/{seriesId}/start-templates/{startId}/award-policy",
                        template.getId(), five)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rankingBasis":"GUN_TIME","primaryStandingMode":"BY_GENDER",
                                 "absolutePrizePlaces":3,"categoryEnabled":false,"ageCalculationMode":"EVENT_DATE",
                                 "categoryPrizePlaces":0,"excludeAbsoluteWinnersFromCategory":false}
                                """))
                .andExpect(status().isOk());

        String command = """
                {"event":{"eventSeriesId":%d,"name":"Казань","startsAt":"2027-08-15T06:00:00Z",
                  "location":"Казань","timeZone":"Europe/Moscow","publicationStatus":"DRAFT"},
                 "starts":[
                   {"templateStartId":%d,"name":"5,2 км","distanceMeters":5200,"sourceCode":null,
                    "publicVisible":true,"awardPolicy":null},
                   {"templateStartId":null,"name":"Детский забег","distanceMeters":1000,"sourceCode":null,
                    "publicVisible":false,"awardPolicy":null}
                 ]}
                """.formatted(template.getId(), five);
        MvcResult result = mockMvc.perform(post("/api/admin/events/with-starts")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(command))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.starts.length()").value(2))
                .andExpect(jsonPath("$.starts[0].name").value("5,2 км"))
                .andExpect(jsonPath("$.starts[0].sourceCode").value("template-start-" + five))
                .andExpect(jsonPath("$.starts[0].displayOrder").value(0))
                .andExpect(jsonPath("$.starts[1].name").value("Детский забег"))
                .andExpect(jsonPath("$.starts[1].sourceCode").value("start-2"))
                .andReturn();
        long eventId = objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("event").get("id").asLong();
        List<Race> copied = raceRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId);
        assertThat(copied).extracting(Race::getName).containsExactly("5,2 км", "Детский забег");
        assertThat(copied).allSatisfy(race -> assertThat(race.getEvent().getId()).isEqualTo(eventId));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema='public' AND table_name='races' AND column_name='template_start_id'
                """, Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ranking_basis FROM award_policies WHERE race_id=?", String.class, copied.get(0).getId()))
                .isEqualTo("GUN_TIME");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ranking_basis FROM award_policies WHERE race_id=?", String.class, copied.get(1).getId()))
                .isEqualTo("CHIP_TIME");

        mockMvc.perform(put("/api/admin/event-series/{seriesId}/start-templates/{startId}",
                        template.getId(), five).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Переименован в шаблоне","distanceMeters":5000,
                                 "publicVisible":true}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/event-series/{seriesId}/start-templates/{startId}",
                        template.getId(), excluded).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());
        createTemplateStart(template.getId(), "Новый шаблонный старт", null);
        assertThat(raceRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId))
                .extracting(Race::getName).containsExactly("5,2 км", "Детский забег");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_series_start_templates WHERE event_series_id=?",
                Long.class, template.getId())).isEqualTo(2);

        long eventsBefore = eventRepository.countByEventSeriesId(template.getId());
        String duplicateCodes = """
                {"event":{"eventSeriesId":%d,"name":"Rollback","timeZone":"Europe/Moscow"},
                 "starts":[
                   {"name":"A","sourceCode":"same","publicVisible":true},
                   {"name":"B","sourceCode":"SAME","publicVisible":true}
                 ]}
                """.formatted(template.getId());
        mockMvc.perform(post("/api/admin/events/with-starts")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(duplicateCodes))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RACE_SOURCE_CODE_DUPLICATE"));
        assertThat(eventRepository.countByEventSeriesId(template.getId())).isEqualTo(eventsBefore);
    }

    @Test
    void bulkCopyUsesTheSameStartMaterializationAndCreatesIndependentRaceSets() throws Exception {
        EventSeries template = createSeries("Bulk starts", "bulk-starts");
        long five = createTemplateStart(template.getId(), "5 км", 5000);
        long ten = createTemplateStart(template.getId(), "10 км", 10000);
        mockMvc.perform(put("/api/admin/event-series/{seriesId}/start-templates/{startId}/award-policy",
                        template.getId(), five)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rankingBasis":"GUN_TIME","primaryStandingMode":"BY_GENDER",
                                 "absolutePrizePlaces":3,"categoryEnabled":true,"ageCalculationMode":"EVENT_DATE",
                                 "categoryPrizePlaces":2,"excludeAbsoluteWinnersFromCategory":true}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/event-series/{seriesId}/start-templates/{startId}/categories",
                        template.getId(), five)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceName":"18+","displayName":"18+","minAge":18,"maxAge":null,
                                 "gender":null,"displayOrder":0,"enabled":true}
                                """))
                .andExpect(status().isCreated());
        String command = """
                {"eventSeriesId":%d,"date":"2027-08-15",
                 "events":[
                   {"name":"Казань","location":"Казань","timeZone":"Europe/Moscow"},
                   {"name":"Омск","location":"Омск","timeZone":"Asia/Omsk"},
                   {"name":"Иркутск","location":"Иркутск","timeZone":"Asia/Irkutsk"}],
                 "starts":[
                   {"templateStartId":%d,"name":"5 км","distanceMeters":5000,"sourceCode":"5K","publicVisible":true},
                   {"templateStartId":%d,"name":"10 км","distanceMeters":10000,"sourceCode":"10K","publicVisible":true},
                   {"name":"Детский забег","distanceMeters":1000,"sourceCode":"KIDS","publicVisible":true}
                 ]}
                """.formatted(template.getId(), five, ten);
        mockMvc.perform(post("/api/admin/events/bulk/preview")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(command))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startsPerEvent").value(3))
                .andExpect(jsonPath("$.totalStartCount").value(9))
                .andExpect(jsonPath("$.starts[2].name").value("Детский забег"));
        mockMvc.perform(post("/api/admin/events/bulk")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(command))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.events.length()").value(3))
                .andExpect(jsonPath("$.totalStartCount").value(9));

        List<Event> events = eventRepository.findAllByEventSeriesIdOrderByIdAsc(template.getId());
        assertThat(events).hasSize(3);
        Set<Long> allRaceIds = new HashSet<>();
        Set<Long> allCategoryIds = new HashSet<>();
        for (Event event : events) {
            List<Race> races = raceRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(event.getId());
            assertThat(races).extracting(Race::getName).containsExactly("5 км", "10 км", "Детский забег");
            assertThat(races).extracting(Race::getDisplayOrder).containsExactly(0, 1, 2);
            races.forEach(race -> assertThat(allRaceIds.add(race.getId())).isTrue());
            List<Category> copiedCategories = categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(
                    races.getFirst().getId());
            assertThat(copiedCategories).extracting(Category::getDisplayName).containsExactly("18+");
            assertThat(allCategoryIds.add(copiedCategories.getFirst().getId())).isTrue();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT category_enabled FROM award_policies WHERE race_id=?",
                    Boolean.class, races.getFirst().getId())).isTrue();
        }
        assertThat(allRaceIds).hasSize(9);
        assertThat(allCategoryIds).hasSize(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_series_start_templates WHERE event_series_id=?",
                Long.class, template.getId())).isEqualTo(2);
    }

    @Test
    void deletesOnlyAnEmptyDraftRaceAndKeepsTemplateOtherEventsAndPublishedRaceUntouched() throws Exception {
        EventSeries template = createSeries("Delete safety", "delete-safety");
        createTemplateStart(template.getId(), "Шаблонный старт", 5000);
        Event event = createEvent(template, "Current", "delete-current", "Казань", "2027-08-15T06:00:00Z");
        Event otherEvent = createEvent(template, "Other", "delete-other", "Омск", "2027-08-16T06:00:00Z");
        Race published = createDraftRace(event, "published", 0);
        published.setResultsPublicationStatus(ResultsPublicationStatus.PUBLISHED);
        raceRepository.saveAndFlush(published);
        Race emptyDraft = createDraftRace(event, "empty", 1);
        Race otherRace = createDraftRace(otherEvent, "other", 0);

        mockMvc.perform(delete("/api/admin/events/{eventId}/races/{raceId}", event.getId(), emptyDraft.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(raceRepository.findById(emptyDraft.getId())).isEmpty();
        assertThat(raceRepository.findById(published.getId())).get()
                .extracting(Race::getResultsPublicationStatus).isEqualTo(ResultsPublicationStatus.PUBLISHED);
        assertThat(raceRepository.findById(otherRace.getId())).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM event_series_start_templates WHERE event_series_id=?",
                Long.class, template.getId())).isEqualTo(1);
        assertThat(changeLogRepository.findAllByEntityTypeAndEntityIdOrderByChangedAtAsc(
                AuditEntityType.RACE, emptyDraft.getId())).extracting(AdminChangeLog::getFieldName)
                .contains("deleted");
    }

    @Test
    void rejectsRaceDeletionForPublishedOrPreviouslyPublishedResults() throws Exception {
        EventSeries series = createSeries("Publication guard", "publication-guard");
        Event event = createEvent(series, "Guard", "publication-delete-guard", "Казань", "2027-08-15T06:00:00Z");
        Race race = createDraftRace(event, "guard", 0);
        race.setResultsPublicationStatus(ResultsPublicationStatus.PUBLISHED);
        raceRepository.saveAndFlush(race);

        mockMvc.perform(delete("/api/admin/events/{eventId}/races/{raceId}", event.getId(), race.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RACE_DELETE_PUBLISHED"));

        race.setResultsPublicationStatus(ResultsPublicationStatus.DRAFT);
        raceRepository.saveAndFlush(race);
        RaceResultPublicationHistory history = new RaceResultPublicationHistory();
        history.setRace(race);
        history.setFromStatus(ResultsPublicationStatus.PUBLISHED);
        history.setToStatus(ResultsPublicationStatus.DRAFT);
        history.setActor(ADMIN_USERNAME);
        history.setReason("test");
        history.setCreatedAt(Instant.now());
        raceResultPublicationHistoryRepository.saveAndFlush(history);

        mockMvc.perform(delete("/api/admin/events/{eventId}/races/{raceId}", event.getId(), race.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RACE_DELETE_HAS_DEPENDENCIES"));
        assertThat(raceRepository.findById(race.getId())).isPresent();
    }

    @Test
    void rejectsRaceDeletionWhenRegistrationsOrResultsExist() throws Exception {
        EventSeries series = createSeries("Facts guard", "facts-guard");
        Event event = createEvent(series, "Facts", "facts-delete-guard", "Казань", "2027-08-15T06:00:00Z");
        Race registrationRace = createDraftRace(event, "registration", 0);
        Race resultRace = createDraftRace(event, "result", 1);
        ImportBatch registrationBatch = raceImportBatch(event, registrationRace, "registration.csv");
        ImportBatch resultBatch = raceImportBatch(event, resultRace, "result.csv");
        Registration registrationOnly = raceRegistration(registrationRace, registrationBatch, 1, "101");
        Registration withResult = raceRegistration(resultRace, resultBatch, 1, "102");
        Result result = new Result();
        result.setRegistration(withResult);
        result.setStatus("finished");
        resultRepository.saveAndFlush(result);

        mockMvc.perform(delete("/api/admin/events/{eventId}/races/{raceId}", event.getId(), registrationRace.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RACE_DELETE_HAS_REGISTRATIONS"));
        mockMvc.perform(delete("/api/admin/events/{eventId}/races/{raceId}", event.getId(), resultRace.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RACE_DELETE_HAS_RESULTS"));
        assertThat(registrationRepository.findById(registrationOnly.getId())).isPresent();
        assertThat(resultRepository.findById(result.getId())).isPresent();
    }

    @Test
    void managesOptionalScheduleAndInfoInStableOrder() throws Exception {
        Event event = createPublishedEvent("Content", "content");
        event.setStartsAt(Instant.parse("2027-06-15T06:00:00Z"));
        eventRepository.saveAndFlush(event);
        eventContentService.updateParticipantInfo(event.getId(),
                new ru.sportsresults.api.dto.UpdateEventParticipantInfoRequest(
                        "Коротко", "Лужники", "Москва", null,
                        new java.math.BigDecimal("55.715000"), new java.math.BigDecimal("37.553000"), null),
                ADMIN_USERNAME);
        eventContentService.createScheduleItem(event.getId(),
                new ru.sportsresults.api.dto.UpsertEventScheduleItemRequest(
                        java.time.LocalDateTime.parse("2027-06-15T09:30:00"), null, "Старт", null, 2),
                ADMIN_USERNAME);
        eventContentService.createScheduleItem(event.getId(),
                new ru.sportsresults.api.dto.UpsertEventScheduleItemRequest(
                        java.time.LocalDateTime.parse("2027-06-15T08:00:00"), null, "Открытие", null, 5),
                ADMIN_USERNAME);
        eventContentService.createInfoBlock(event.getId(),
                new ru.sportsresults.api.dto.UpsertEventInfoBlockRequest("Как добраться", "Метро Спортивная", 1),
                ADMIN_USERNAME);

        assertThat(eventContentService.listSchedule(event.getId())).extracting(ru.sportsresults.api.dto.EventScheduleItemDto::title)
                .containsExactly("Открытие", "Старт");
        assertThatThrownBy(() -> eventContentService.createScheduleItem(event.getId(),
                new ru.sportsresults.api.dto.UpsertEventScheduleItemRequest(
                        java.time.LocalDateTime.parse("2027-06-15T10:00:00"),
                        java.time.LocalDateTime.parse("2027-06-15T09:00:00"), "Ошибка", null, 0),
                ADMIN_USERNAME)).isInstanceOf(InvalidRequestException.class);

        mockMvc.perform(get("/api/events/slug/{slug}", event.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participantInfo.venueName").value("Лужники"))
                .andExpect(jsonPath("$.schedule[0].title").value("Открытие"))
                .andExpect(jsonPath("$.infoBlocks[0].title").value("Как добраться"));
    }

    @Test
    void filtersByRaceClusterWithoutRenumberingAchievementsAndRejectsCrossRaceAssignment() {
        Event event = createPublishedEvent("Clusters", "clusters");
        importPublishedFixture(event.getId(), "ranking.csv", rankingCsv().getBytes(StandardCharsets.UTF_8));
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        Race secondRace = raceRepository.findByEventIdAndSourceCode(event.getId(), "10 km").orElseThrow();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 2, false,
                AgeCalculationMode.EVENT_DATE, 0, false), ADMIN_USERNAME);

        var clusterA = startClusterService.create(event.getId(), race.getId(),
                new ru.sportsresults.api.dto.UpsertStartClusterRequest("A", null, "Кластер A", 1,
                        java.time.LocalDateTime.parse("2027-06-15T09:40:00")), ADMIN_USERNAME);
        var clusterB = startClusterService.create(event.getId(), race.getId(),
                new ru.sportsresults.api.dto.UpsertStartClusterRequest("B", null, "Кластер B", 2, null),
                ADMIN_USERNAME);
        var otherRaceCluster = startClusterService.create(event.getId(), secondRace.getId(),
                new ru.sportsresults.api.dto.UpsertStartClusterRequest("X", null, "Другой", 1, null),
                ADMIN_USERNAME);
        Registration registration = registrationRepository.findAllByRaceIdAndBib(race.getId(), "1").getFirst();
        long revisionBeforeClusterAssignment = eventRepository.findById(event.getId())
                .orElseThrow().getResultDataRevision();
        adminResultService.updateRegistration(registration.getId(), registrationRequest(registration, clusterA.id()), ADMIN_USERNAME);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revisionBeforeClusterAssignment + 1);

        var all = resultQueryService.search(event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "place", "asc");
        int officialPlace = item(all.content(), registration.getDisplayName()).rankingAchievements().getFirst().place();
        var filtered = resultQueryService.search(event.getId(), race.getId(), null, null, null, null,
                clusterA.id(), null, 0, 20, "place", "asc");
        assertThat(filtered.content()).singleElement().satisfies(row ->
                assertThat(row.rankingAchievements().getFirst().place()).isEqualTo(officialPlace));
        assertThat(startClusterService.list(event.getId(), race.getId())).hasSize(2);
        assertThatThrownBy(() -> startClusterService.delete(event.getId(), race.getId(), clusterA.id(), ADMIN_USERNAME))
                .isInstanceOf(RequestConflictException.class);
        assertThatThrownBy(() -> adminResultService.updateRegistration(
                registration.getId(), registrationRequest(registration, otherRaceCluster.id()), ADMIN_USERNAME))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("registration race");
        startClusterService.delete(event.getId(), race.getId(), clusterB.id(), ADMIN_USERNAME);
    }

    @Test
    void storesPdfThroughAbstractionAndEnforcesPublicationPrivacyAndOwnership() throws Exception {
        Event event = createPublishedEvent("Documents", "documents");
        byte[] pdf = "%PDF-1.4\n1 0 obj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        var document = eventDocumentService.upload(event.getId(), ru.sportsresults.domain.EventDocumentType.PARTICIPANT_GUIDE,
                "Гайд участника", 0, true, "guide.pdf", MediaType.APPLICATION_PDF_VALUE, pdf, ADMIN_USERNAME);
        assertThat(document.contentUrl()).isEqualTo("/api/events/" + event.getId() + "/documents/" + document.id() + "/content");
        assertThat(eventDocumentService.listPublic(event.getId())).singleElement()
                .extracting(ru.sportsresults.api.dto.EventDocumentDto::sha256).isEqualTo(document.sha256());
        assertThat(eventDocumentService.openPublic(event.getId(), document.id()).resource().getInputStream().readAllBytes())
                .isEqualTo(pdf);

        Event other = createPublishedEvent("Other Documents", "other-documents");
        assertThatThrownBy(() -> eventDocumentService.openPublic(other.getId(), document.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> fileStorageService.open("../guide.pdf"))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> eventDocumentService.upload(event.getId(),
                ru.sportsresults.domain.EventDocumentType.OTHER, "Текст", 0, true, "text.pdf",
                MediaType.APPLICATION_PDF_VALUE, "not-pdf".getBytes(StandardCharsets.UTF_8), ADMIN_USERNAME))
                .isInstanceOf(InvalidRequestException.class);

        eventService.updatePublication(event.getId(), new ru.sportsresults.api.dto.UpdateEventPublicationRequest(
                EventPublicationStatus.DRAFT, ru.sportsresults.domain.ResultsPublicationStatus.DRAFT), ADMIN_USERNAME);
        assertThatThrownBy(() -> eventDocumentService.listPublic(event.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        eventDocumentService.delete(event.getId(), document.id(), ADMIN_USERNAME);
    }

    private static ru.sportsresults.api.dto.UpdateRegistrationRequest registrationRequest(
            Registration registration, Long clusterId) {
        return new ru.sportsresults.api.dto.UpdateRegistrationRequest(
                registration.getDisplayName(), registration.getFirstName(), registration.getLastName(),
                registration.getBirthDate(), registration.getGender(), registration.getBib(),
                registration.getSourceCategory(), clusterId, registration.getEntryKind(),
                registration.getCategory() == null ? null : registration.getCategory().getId());
    }

    private static ResultListItemDto item(List<ResultListItemDto> items, String displayName) {
        return items.stream().filter(item -> item.displayName().equals(displayName)).findFirst().orElseThrow();
    }

    private static RankingAchievementDto achievement(
            List<ResultListItemDto> items,
            String displayName,
            String type
    ) {
        return item(items, displayName).rankingAchievements().stream()
                .filter(achievement -> achievement.type().name().equals(type))
                .findFirst()
                .orElseThrow();
    }

    private Map<Long, List<RankingAchievementDto>> achievementMap(
            Long eventId,
            Long raceId,
            String sort,
            String direction
    ) {
        Map<Long, List<RankingAchievementDto>> achievements = new LinkedHashMap<>();
        int page = 0;
        PageResponse<ResultListItemDto> response;
        do {
            response = resultQueryService.search(
                    eventId, raceId, null, null, null, null, null,
                    page, 200, sort, direction
            );
            response.content().forEach(item -> achievements.put(item.resultId(), item.rankingAchievements()));
            page++;
        } while (page < response.totalPages());
        return Map.copyOf(achievements);
    }

    private AdminIssueFixture createAdminIssueFixture(String name, String slug, int count) {
        Event event = createPublishedEvent(name, slug);
        assertThat(importPublishedFixture(
                event.getId(), slug + ".csv", adminIssueCsv(count).getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        List<Registration> registrations = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            registrations.add(registrationRepository.findAllByRaceEventIdAndBib(
                    event.getId(), "Q-" + index
            ).getFirst());
        }
        assertThat(registrations).hasSize(count);

        ResultIssueStatus[] statuses = {
                ResultIssueStatus.NEW,
                ResultIssueStatus.IN_PROGRESS,
                ResultIssueStatus.RESOLVED,
                ResultIssueStatus.REJECTED,
                ResultIssueStatus.NEW,
                ResultIssueStatus.IN_PROGRESS
        };
        List<ResultIssueRequest> issues = new ArrayList<>();
        for (int index = 0; index < registrations.size(); index++) {
            ResultIssueType type = index == 2 || index == 3 || index == 4
                    ? ResultIssueType.RESULT_CORRECTION
                    : ResultIssueType.MISSING_RESULT;
            ResultCorrectionReason reason = switch (index) {
                case 2 -> ResultCorrectionReason.OFFICIAL_TIME;
                case 3 -> ResultCorrectionReason.CHIP_TIME;
                case 4 -> ResultCorrectionReason.OTHER;
                default -> null;
            };
            issues.add(createAdminIssue(
                    event,
                    registrations.get(index),
                    type,
                    statuses[index % statuses.length],
                    reason,
                    index + 1
            ));
        }
        return new AdminIssueFixture(event, registrations, List.copyOf(issues));
    }

    private AdminIssueFixture createGlobalIssueFixture(
            String name,
            String slug,
            String location,
            String startsAt,
            int count
    ) {
        Event event = createPublishedEvent(name, slug);
        event.setLocation(location);
        event.setStartsAt(Instant.parse(startsAt));
        event = eventRepository.saveAndFlush(event);
        assertThat(importPublishedFixture(
                event.getId(), slug + ".csv", adminIssueCsv(count).getBytes(StandardCharsets.UTF_8)
        ).status()).isEqualTo(ImportBatchStatus.SUCCEEDED);
        List<Registration> registrations = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            registrations.add(registrationRepository.findAllByRaceEventIdAndBib(
                    event.getId(), "Q-" + index
            ).getFirst());
        }
        Race fixtureRace = registrations.getFirst().getRace();
        Category fixtureCategory = categoryRepository.findByRaceIdAndSourceName(fixtureRace.getId(), "Open")
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setRace(fixtureRace);
                    category.setSourceName("Open");
                    category.setDisplayName("Open historical");
                    category.setDisplayOrder(0);
                    return categoryRepository.saveAndFlush(category);
                });
        registrations.forEach(registration -> registration.setCategory(fixtureCategory));
        registrationRepository.saveAllAndFlush(registrations);
        registrations = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            registrations.add(registrationRepository.findAllByRaceEventIdAndBib(
                    event.getId(), "Q-" + index
            ).getFirst());
        }
        ResultIssueStatus[] statuses = {
                ResultIssueStatus.NEW,
                ResultIssueStatus.IN_PROGRESS,
                ResultIssueStatus.RESOLVED,
                ResultIssueStatus.REJECTED,
                ResultIssueStatus.NEW,
                ResultIssueStatus.IN_PROGRESS
        };
        List<ResultIssueRequest> issues = new ArrayList<>();
        for (int index = 0; index < registrations.size(); index++) {
            ResultIssueType type = index >= 2 && index <= 4
                    ? ResultIssueType.RESULT_CORRECTION
                    : ResultIssueType.MISSING_RESULT;
            ResultCorrectionReason reason = switch (index) {
                case 2 -> ResultCorrectionReason.OFFICIAL_TIME;
                case 3 -> ResultCorrectionReason.CHIP_TIME;
                case 4 -> ResultCorrectionReason.OTHER;
                default -> null;
            };
            issues.add(createGlobalIssue(
                    event, registrations.get(index), type, statuses[index % statuses.length], reason, index + 1
            ));
        }
        return new AdminIssueFixture(event, List.copyOf(registrations), List.copyOf(issues));
    }

    private void insertSyntheticJournalRows(
            AdminIssueFixture fixture,
            String bibPrefix,
            int firstSourceRow,
            int count
    ) {
        Registration seed = fixture.registrations().getFirst();
        int lastSourceRow = firstSourceRow + count - 1;
        jdbcTemplate.update("""
                INSERT INTO registrations(
                    race_id, import_batch_id, entry_kind, bib, display_name,
                    source_row_number, source_row_hash, created_at, updated_at
                )
                SELECT ?, ?, 'PERSON', ? || lpad(g::text, 6, '0'), 'Synthetic runner ' || g,
                       g, repeat(md5((? || g)::text), 2), now(), now()
                FROM generate_series(?, ?) g
                """, seed.getRace().getId(), seed.getImportBatch().getId(), bibPrefix, bibPrefix,
                firstSourceRow, lastSourceRow);
        jdbcTemplate.update("""
                INSERT INTO result_issue_requests(
                    event_id, registration_id, issue_type, status, contact_email, message,
                    created_at, updated_at, snapshot_origin,
                    snapshot_event_name, snapshot_event_location, snapshot_event_starts_at,
                    snapshot_sport_format_id, snapshot_sport_format_name, snapshot_sport_format_code,
                    snapshot_race_id, snapshot_race_name, snapshot_race_code,
                    snapshot_bib, snapshot_display_name, snapshot_import_batch_id, snapshot_source_row_number
                )
                SELECT ?, registration.id, 'MISSING_RESULT',
                       CASE registration.source_row_number % 4
                           WHEN 0 THEN 'NEW' WHEN 1 THEN 'IN_PROGRESS'
                           WHEN 2 THEN 'RESOLVED' ELSE 'REJECTED' END,
                       'synthetic@example.org', 'Synthetic performance fixture',
                       TIMESTAMPTZ '2026-01-01 00:00:00Z'
                           + registration.source_row_number * INTERVAL '1 second',
                       TIMESTAMPTZ '2026-01-01 00:00:00Z'
                           + registration.source_row_number * INTERVAL '1 second',
                       'CAPTURED_AT_CREATION', ?, ?, ?, ?, ?, ?, ?, ?, ?,
                       registration.bib, registration.display_name,
                       registration.import_batch_id, registration.source_row_number
                FROM registrations registration
                WHERE registration.import_batch_id=?
                  AND registration.source_row_number BETWEEN ? AND ?
                """,
                fixture.event().getId(),
                fixture.event().getName(), fixture.event().getLocation(),
                java.sql.Timestamp.from(fixture.event().getStartsAt()),
                7_001L, "Legacy format", "legacy-format",
                seed.getRace().getId(), seed.getRace().getName(), seed.getRace().getSourceCode(),
                seed.getImportBatch().getId(), firstSourceRow, lastSourceRow
        );
    }

    private String explain(String sql) {
        return String.join("\n", jdbcTemplate.queryForList(
                "EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) " + sql,
                String.class
        ));
    }

    private ResultIssueRequest createGlobalIssue(
            Event event,
            Registration registration,
            ResultIssueType type,
            ResultIssueStatus status,
            ResultCorrectionReason correctionReason,
            int sequence
    ) {
        ResultIssueRequest issue = createAdminIssue(
                event, registration, type, status, correctionReason, sequence
        );
        if (type == ResultIssueType.MISSING_RESULT) {
            issue.setResult(null);
            issue = resultIssueRequestRepository.saveAndFlush(issue);
            jdbcTemplate.update(
                    "UPDATE result_issue_requests SET snapshot_ranking='[]'::jsonb WHERE id=?",
                    issue.getId()
            );
        }
        return issue;
    }

    private ResultIssueRequest createAdminIssue(
            Event event,
            Registration registration,
            ResultIssueType type,
            ResultIssueStatus status,
            ResultCorrectionReason correctionReason,
            int sequence
    ) {
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();
        ResultIssueRequest issue = new ResultIssueRequest();
        issue.setEvent(event);
        issue.setRegistration(registration);
        issue.setResult(result);
        issue.setIssueType(type);
        issue.setStatus(status);
        issue.setContactEmail("runner" + sequence + "@example.org");
        issue.setMessage("Проверка обращения " + sequence);
        if (type == ResultIssueType.RESULT_CORRECTION) {
            issue.setCorrectionReason(correctionReason);
            issue.setClaimedGunTime(Duration.ofMillis(12_000L + sequence));
            issue.setClaimedChipTime(Duration.ofMillis(11_000L + sequence));
            issue.setObservedGunTime(result.getGunTime());
            issue.setObservedChipTime(result.getChipTime());
            issue.setObservedResultStatus(result.getStatus());
        } else {
            issue.setEstimatedStartAt(Instant.parse("2026-08-30T06:00:00Z").plusSeconds(sequence));
            issue.setEstimatedFinishAt(Instant.parse("2026-08-30T07:00:00Z").plusSeconds(sequence));
        }
        if (status == ResultIssueStatus.RESOLVED || status == ResultIssueStatus.REJECTED) {
            issue.setResolvedAt(Instant.now());
        }
        resultIssueSnapshotService.capture(issue);
        return resultIssueRequestRepository.saveAndFlush(issue);
    }

    private ResultIssueAttachment createAdminAttachment(
            ResultIssueRequest issue,
            String fileName,
            AttachmentUploadStatus uploadStatus,
            AttachmentScanStatus scanStatus
    ) {
        Instant now = Instant.now();
        ResultIssueAttachment attachment = new ResultIssueAttachment();
        attachment.setIssueRequest(issue);
        attachment.setOriginalFileName(fileName);
        attachment.setStorageKey("admin-tests/" + issue.getId() + "/" + UUID.randomUUID());
        attachment.setContentType(fileName.endsWith(".pdf") ? "application/pdf" : "video/mp4");
        attachment.setSizeBytes(650_000_000L);
        attachment.setUploadStatus(uploadStatus);
        attachment.setScanStatus(scanStatus);
        attachment.setUploadAuthorizationExpiresAt(now.plus(Duration.ofMinutes(15)));
        attachment.setRetentionExpiresAt(now.plus(Duration.ofDays(90)));
        if (uploadStatus == AttachmentUploadStatus.UPLOADED) {
            attachment.setUploadedAt(now);
        }
        if (scanStatus != AttachmentScanStatus.PENDING) {
            attachment.setDetectedContentType(attachment.getContentType());
            attachment.setScannedAt(now);
        }
        if (uploadStatus == AttachmentUploadStatus.DELETED) {
            attachment.setDeletedAt(now);
        }
        return resultIssueAttachmentRepository.saveAndFlush(attachment);
    }

    private void normalizeFixtureIssuesToNew(List<ResultIssueRequest> issues) {
        issues.forEach(issue -> {
            issue.setStatus(ResultIssueStatus.NEW);
            issue.setResolvedAt(null);
        });
        resultIssueRequestRepository.saveAllAndFlush(issues);
    }

    private void transitionStatus(
            AdminIssueFixture fixture,
            int issueIndex,
            ResultIssueStatus expected,
            ResultIssueStatus next
    ) throws Exception {
        mockMvc.perform(put(
                        "/api/admin/events/{eventId}/result-issue-requests/{issueId}/status",
                        fixture.event().getId(), fixture.issues().get(issueIndex).getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedStatus":"%s","status":"%s"}
                                """.formatted(expected, next)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(next.name()));
    }

    private Callable<Boolean> concurrentStatusUpdate(
            CountDownLatch ready,
            CountDownLatch start,
            Long eventId,
            Long issueId,
            ResultIssueStatus next
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent updates did not start in time");
            }
            try {
                adminResultIssueService.updateStatus(
                        eventId,
                        issueId,
                        new ru.sportsresults.api.dto.UpdateResultIssueStatusRequest(
                                ResultIssueStatus.NEW, next, null
                        ),
                        ADMIN_USERNAME
                );
                return true;
            } catch (RequestConflictException exception) {
                return false;
            }
        };
    }

    private Callable<Boolean> concurrentIssueInsert(
            CountDownLatch ready,
            CountDownLatch start,
            Long eventId,
            Long registrationId,
            Long resultId,
            String issueType,
            String correctionReason,
            String observedResultStatus
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent inserts did not start in time");
            }
            try {
                jdbcTemplate.update("""
                        INSERT INTO result_issue_requests(
                            event_id, registration_id, result_id, issue_type, correction_reason, status,
                            contact_email, message, observed_result_status, snapshot_origin,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, 'NEW', 'runner@example.org', 'Параллельная проверка',
                                  ?, 'CAPTURED_AT_CREATION', now(), now())
                        """,
                        eventId,
                        registrationId,
                        resultId,
                        issueType,
                        correctionReason,
                        observedResultStatus
                );
                return true;
            } catch (DataIntegrityViolationException exception) {
                return false;
            }
        };
    }

    private CreatedIssueAccess createMissingIssueAccess(
            Long eventId,
            String bib,
            String birthDate
    ) throws Exception {
        var creation = mockMvc.perform(post(
                        "/api/events/{eventId}/result-issue-requests/missing", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bib":"%s","birthDate":"%s","contactEmail":"runner@example.org",
                                 "message":"Результат отсутствует"}
                                """.formatted(bib, birthDate)))
                .andReturn();
        String response = creation.getResponse().getContentAsString();
        assertThat(creation.getResponse().getStatus())
                .withFailMessage("Expected issue creation to return 201, response: %s", response)
                .isEqualTo(201);
        var json = objectMapper.readTree(response);
        assertThat(json.get("attachmentUploadToken").isTextual()).isTrue();
        assertThat(json.get("attachmentUploadTokenExpiresAt")).isNotNull();
        return new CreatedIssueAccess(
                json.get("issueId").asLong(),
                json.get("attachmentUploadToken").asText()
        );
    }

    @Test
    void emergencyReplacementRetiresOldDatasetArchivesIssuesAndExposesOnlyNewCurrentRows() {
        Event configuredEvent = createOpenInquiryEvent("Stage F emergency", "stage-f-emergency");
        configuredEvent.setLocation("Kazan");
        Event event = eventRepository.saveAndFlush(configuredEvent);
        importPublishedFixture(
                event.getId(), "wrong-city.csv",
                singleInquiryBibCsv("Старый", "finished").getBytes(StandardCharsets.UTF_8)
        );
        Race race = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId()).getFirst();
        awardPolicyService.upsert(race.getId(), new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME, PrimaryStandingMode.ALL, 0, false,
                AgeCalculationMode.EVENT_DATE, 0, false
        ), ADMIN_USERNAME);
        Registration oldRegistration = registrationRepository
                .findAllByRaceEventIdAndBib(event.getId(), "1100").getFirst();
        Result oldResult = resultRepository.findByRegistrationId(oldRegistration.getId()).orElseThrow();
        Long checkpointId = jdbcTemplate.queryForObject("""
                INSERT INTO checkpoints(race_id, code, name, sequence_number, created_at, updated_at)
                VALUES (?, 'CP-1', 'Intermediate', 1, now(), now()) RETURNING id
                """, Long.class, race.getId());
        Long oldSplitId = jdbcTemplate.queryForObject("""
                INSERT INTO splits(result_id, checkpoint_id, gun_time_ms, chip_time_ms, created_at, updated_at)
                VALUES (?, ?, 500, 450, now(), now()) RETURNING id
                """, Long.class, oldResult.getId(), checkpointId);
        ResultIssueRequest resolvedIssue = resultIssue(
                event, oldRegistration, oldResult,
                ResultIssueType.RESULT_CORRECTION, ResultCorrectionReason.OFFICIAL_TIME, "finished"
        );
        resolvedIssue.setStatus(ResultIssueStatus.RESOLVED);
        resolvedIssue = resultIssueRequestRepository.saveAndFlush(resolvedIssue);
        ResultIssueRequest oldIssue = resultIssue(
                event, oldRegistration, oldResult,
                ResultIssueType.RESULT_CORRECTION, ResultCorrectionReason.OFFICIAL_TIME, "finished"
        );
        oldIssue.setStatus(ResultIssueStatus.IN_PROGRESS);
        oldIssue = resultIssueRequestRepository.saveAndFlush(oldIssue);
        Map<String, Object> oldSnapshot = resultIssueSnapshotState(oldIssue.getId());

        byte[] corrected = singleInquiryBibCsv("Новый", "finished").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> importPreviewService.preview(
                event.getId(), "correct.csv", corrected, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("RACE_RESULTS_MUST_BE_DRAFT");

        raceResultsPublicationService.draft(event.getId(), race.getId(), "Emergency replacement", ADMIN_USERNAME);
        long revision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "correct.csv", corrected, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(preview.emergencySummary()).isNotNull();
        assertThat(preview.emergencySummary().dangerousOperation()).isTrue();
        assertThat(preview.emergencySummary().event().name()).isEqualTo("Stage F emergency");
        assertThat(preview.emergencySummary().event().location()).isEqualTo("Kazan");
        assertThat(preview.emergencySummary().file().filename()).isEqualTo("correct.csv");
        assertThat(preview.emergencySummary().races()).singleElement().satisfies(summary -> {
            assertThat(summary.resultsPublicationStatus()).isEqualTo("DRAFT");
            assertThat(summary.currentCount()).isOne();
            assertThat(summary.sourceCount()).isOne();
            assertThat(summary.wouldRetireCount()).isOne();
            assertThat(summary.wouldInsertCount()).isOne();
            assertThat(summary.activeIssuesWouldArchiveCount()).isOne();
        });
        assertThat(preview.modeSummary().wouldUpdate()).isZero();

        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), preview.operationId(), (new String(corrected, StandardCharsets.UTF_8) + "\n")
                        .getBytes(StandardCharsets.UTF_8), ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("FILE_MISMATCH");

        ImportApplyResponseDto applied = importApplyService.apply(
                event.getId(), preview.operationId(), corrected, ADMIN_USERNAME
        );
        assertThat(applied.insertedCount()).isOne();
        assertThat(applied.updatedCount()).isZero();
        assertThat(applied.retiredCount()).isOne();
        assertThat(applied.archivedIssueCount()).isOne();
        assertThat(applied.newRevision()).isEqualTo(revision + 1);
        assertThat(importApplyService.apply(event.getId(), preview.operationId(), corrected, ADMIN_USERNAME))
                .isEqualTo(applied);

        Registration retired = registrationRepository.findById(oldRegistration.getId()).orElseThrow();
        assertThat(retired.getRetiredAt()).isEqualTo(applied.appliedAt());
        assertThat(retired.getRetiredByImportOperationId()).isEqualTo(preview.operationId());
        assertThat(resultRepository.findById(oldResult.getId())).isPresent();
        assertThat(count("splits", "id = " + oldSplitId)).isOne();
        Registration current = registrationRepository
                .findAllByRaceEventIdAndBib(event.getId(), "1100").getFirst();
        Result currentResult = resultRepository.findByRegistrationId(current.getId()).orElseThrow();
        assertThat(current.getId()).isNotEqualTo(oldRegistration.getId());
        assertThat(currentResult.getId()).isNotEqualTo(oldResult.getId());
        assertThat(current.getDisplayName()).isEqualTo("Новый Участник");
        assertThat(registrationRepository.count()).isEqualTo(2);
        assertThat(resultRepository.count()).isEqualTo(2);
        assertThat(registrationRepository.countByRaceEventId(event.getId())).isOne();
        assertThat(resultRepository.countByRegistrationRaceEventId(event.getId())).isOne();

        ResultIssueRequest archivedIssue = resultIssueRequestRepository.findById(oldIssue.getId()).orElseThrow();
        assertThat(archivedIssue.getStatus()).isEqualTo(ResultIssueStatus.IN_PROGRESS);
        assertThat(archivedIssue.getQueueArchiveReason()).isEqualTo(ResultIssueArchiveReason.EMERGENCY_REPLACEMENT);
        assertThat(archivedIssue.getQueueArchivedImportOperationId()).isEqualTo(preview.operationId());
        ResultIssueRequest stillResolved = resultIssueRequestRepository.findById(resolvedIssue.getId()).orElseThrow();
        assertThat(stillResolved.getStatus()).isEqualTo(ResultIssueStatus.RESOLVED);
        assertThat(stillResolved.getQueueArchivedAt()).isNull();
        assertThat(resultIssueSnapshotState(oldIssue.getId())).isEqualTo(oldSnapshot);
        assertThat(resultIssueHistoryRepository.countByIssueRequest_IdAndAction(
                oldIssue.getId(), ResultIssueHistoryAction.QUEUE_ARCHIVED)).isOne();
        var emergencyJournalDetail = context.getBean(ru.sportsresults.service.GlobalResultIssueJournalService.class)
                .get(oldIssue.getId());
        assertThat(emergencyJournalDetail.queueArchiveReason())
                .isEqualTo(ResultIssueArchiveReason.EMERGENCY_REPLACEMENT);
        assertThat(emergencyJournalDetail.queueArchivedImportOperationId()).isEqualTo(preview.operationId());
        assertThat(emergencyJournalDetail.historicalSnapshot().bib()).isEqualTo("1100");
        assertThat(emergencyJournalDetail.currentContext().registrationRetired()).isTrue();
        assertThat(emergencyJournalDetail.currentContext().registrationId()).isEqualTo(oldRegistration.getId());
        assertThat(importOperationItemRepository.findAllByOperationIdOrderBySourceRowNumberAsc(preview.operationId()))
                .extracting(item -> item.getAction().name())
                .containsExactlyInAnyOrder("RETIRE", "INSERT");

        assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.DRAFT);
        assertThat(resultQueryService.searchAdmin(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "bib", "asc", RankingBasis.GUN_TIME
        ).content()).singleElement().satisfies(row -> assertThat(row.resultId()).isEqualTo(currentResult.getId()));
        assertThatThrownBy(() -> adminResultService.updateResult(
                oldResult.getId(),
                new UpdateResultRequest("finished", 2_000L, 1_900L, 1, 1, 1, 1, 1, 1),
                ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("RETIRED_REGISTRATION_NOT_MUTABLE");

        raceResultsPublicationService.publish(event.getId(), race.getId(), ADMIN_USERNAME);
        assertThat(resultQueryService.search(
                event.getId(), race.getId(), null, null, null, null, null,
                0, 20, "place", "asc"
        ).content()).singleElement().satisfies(row -> {
            assertThat(row.resultId()).isEqualTo(currentResult.getId());
            assertThat(row.rankingAchievements()).singleElement()
                    .satisfies(achievement -> assertThat(achievement.place()).isOne());
        });
        assertThatThrownBy(() -> resultQueryService.getPublishedResult(oldResult.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(resultInquiryService.lookup(event.getId(), "1100").publicResultId())
                .isEqualTo(currentResult.getId());
        ResultIssueRequest replacementIssue = resultIssue(
                event, current, currentResult,
                ResultIssueType.RESULT_CORRECTION, ResultCorrectionReason.OFFICIAL_TIME, "finished"
        );
        assertThat(replacementIssue.getRegistration().getId()).isEqualTo(current.getId());
        assertThat(replacementIssue.getId()).isNotEqualTo(oldIssue.getId());

        ImportPreviewResponseDto ordinary = importPreviewService.preview(
                event.getId(), "ordinary.csv", corrected, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(ordinary.rows()).singleElement().satisfies(row -> {
            assertThat(row.decision()).isEqualTo(ru.sportsresults.importing.ImportPreviewDecision.EXISTING_UNCHANGED);
            assertThat(row.matchedRegistrationId()).isEqualTo(current.getId());
        });
    }

    @Test
    void emergencyApplyBecomesStaleWhenAnActiveIssueAppearsAfterPreview() {
        Event event = createPublishedEvent("Stage F issue digest", "stage-f-issue-digest");
        importPublishedFixture(
                event.getId(), "old.csv",
                singleInquiryBibCsv("Старый", "finished").getBytes(StandardCharsets.UTF_8)
        );
        Race race = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId()).getFirst();
        raceResultsPublicationService.draft(event.getId(), race.getId(), "Replacement", ADMIN_USERNAME);
        Registration registration = registrationRepository
                .findAllByRaceEventIdAndBib(event.getId(), "1100").getFirst();
        Result result = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();
        byte[] corrected = singleInquiryBibCsv("Новый", "finished").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "correct.csv", corrected, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        long revision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        ResultIssueRequest issue = resultIssue(
                event, registration, result,
                ResultIssueType.RESULT_CORRECTION, ResultCorrectionReason.OFFICIAL_TIME, "finished"
        );
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision()).isEqualTo(revision);

        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), preview.operationId(), corrected, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_STALE");
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().isCurrent()).isTrue();
        assertThat(resultIssueRequestRepository.findById(issue.getId()).orElseThrow().getQueueArchivedAt()).isNull();
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision()).isEqualTo(revision);
    }

    @Test
    void emergencyApplyRejectsRacePublishedAfterPreviewWithoutSportsMutation() {
        Event event = createPublishedEvent("Stage F publication race", "stage-f-publication-race");
        importPublishedFixture(
                event.getId(), "old.csv",
                singleInquiryBibCsv("Старый", "finished").getBytes(StandardCharsets.UTF_8)
        );
        Race race = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId()).getFirst();
        raceResultsPublicationService.draft(event.getId(), race.getId(), "Replacement", ADMIN_USERNAME);
        byte[] corrected = singleInquiryBibCsv("Новый", "finished").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "correct.csv", corrected, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        long revision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        long batches = importBatchRepository.count();

        raceResultsPublicationService.publish(event.getId(), race.getId(), ADMIN_USERNAME);
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision()).isEqualTo(revision);
        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), preview.operationId(), corrected, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("RACE_RESULTS_MUST_BE_DRAFT");

        assertThat(registrationRepository.count()).isOne();
        assertThat(registrationRepository.countByRaceEventId(event.getId())).isOne();
        assertThat(importBatchRepository.count()).isEqualTo(batches);
        assertThat(importOperationRepository.findById(preview.operationId()).orElseThrow().getStatus())
                .isEqualTo(ImportOperationStatus.PREVIEWED);
    }

    @Test
    void differentEmergencyOperationsBecomeStaleAndANewGenerationCanReplaceAgain() {
        Event event = createPublishedEvent("Stage F generations", "stage-f-generations");
        importPublishedFixture(
                event.getId(), "generation-1.csv",
                singleInquiryBibCsv("Первый", "finished").getBytes(StandardCharsets.UTF_8)
        );
        Race race = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId()).getFirst();
        raceResultsPublicationService.draft(event.getId(), race.getId(), "Replacement", ADMIN_USERNAME);
        byte[] generation2 = singleInquiryBibCsv("Второй", "finished")
                .replace(",1100,", ",2200,")
                .getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto operationA = importPreviewService.preview(
                event.getId(), "generation-2.csv", generation2, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        ImportPreviewResponseDto operationB = importPreviewService.preview(
                event.getId(), "generation-2-copy.csv", generation2, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(operationA.baseRevision()).isEqualTo(operationB.baseRevision());

        ImportApplyResponseDto firstApply = importApplyService.apply(
                event.getId(), operationA.operationId(), generation2, ADMIN_USERNAME
        );
        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), operationB.operationId(), generation2, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_STALE");
        assertThat(importOperationRepository.findById(operationB.operationId()).orElseThrow().getStatus())
                .isEqualTo(ImportOperationStatus.PREVIEWED);

        byte[] retiredOnlyBib = singleInquiryBibCsv("Первый", "finished").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto addRetiredOnly = importPreviewService.preview(
                event.getId(), "retired-only-add.csv", retiredOnlyBib, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(addRetiredOnly.rows()).singleElement().satisfies(row -> {
            assertThat(row.decision()).isEqualTo(ru.sportsresults.importing.ImportPreviewDecision.NEW);
            assertThat(row.futureAction()).isEqualTo(ru.sportsresults.importing.ImportPreviewAction.INSERT);
            assertThat(row.matchedRegistrationId()).isNull();
        });
        ImportPreviewResponseDto updateRetiredOnly = importPreviewService.preview(
                event.getId(), "retired-only-update.csv", retiredOnlyBib, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(updateRetiredOnly.rows()).singleElement().satisfies(row -> {
            assertThat(row.decision()).isEqualTo(ru.sportsresults.importing.ImportPreviewDecision.NEW);
            assertThat(row.futureAction()).isEqualTo(ru.sportsresults.importing.ImportPreviewAction.SKIP);
            assertThat(row.matchedRegistrationId()).isNull();
        });

        byte[] generation3 = singleInquiryBibCsv("Третий", "finished")
                .replace(",1100,", ",3300,")
                .getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto operationC = importPreviewService.preview(
                event.getId(), "generation-3.csv", generation3, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        ImportApplyResponseDto secondApply = importApplyService.apply(
                event.getId(), operationC.operationId(), generation3, ADMIN_USERNAME
        );

        assertThat(secondApply.newRevision()).isEqualTo(firstApply.newRevision() + 1);
        assertThat(registrationRepository.count()).isEqualTo(3);
        assertThat(registrationRepository.countByRaceEventId(event.getId())).isOne();
        assertThat(registrationRepository.findAllCurrentByRaceIds(List.of(race.getId())))
                .singleElement().satisfies(registration -> assertThat(registration.getDisplayName())
                        .isEqualTo("Третий Участник"));
        assertThat(count("registrations", "retired_at IS NOT NULL")).isEqualTo(2);
    }

    @Test
    void emergencyReplacementSupportsExplicitAllRaceScopeAtomically() {
        Event event = createPublishedEvent("Stage F all races", "stage-f-all-races");
        importPublishedFixture(event.getId(), "old.csv", rankingCsv().getBytes(StandardCharsets.UTF_8));
        List<Race> races = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId());
        races.forEach(race -> raceResultsPublicationService.draft(
                event.getId(), race.getId(), "All-race replacement", ADMIN_USERNAME
        ));
        byte[] corrected = (csvHeader() + """
                Казань,Пять,male,1990-01-01,5 km,K-5,Open,finished,5000.0,4900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Казань,Десять,female,1990-01-01,10 km,K-10,Open,finished,6000.0,5900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """).getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "all-correct.csv", corrected, ImportOperationMode.EMERGENCY_REPLACE,
                races.stream().map(Race::getId).toList(), 100, ADMIN_USERNAME
        );
        assertThat(preview.emergencySummary().races()).hasSize(2);
        assertThat(preview.emergencySummary().totals().currentCount()).isEqualTo(4);
        assertThat(preview.emergencySummary().totals().sourceCount()).isEqualTo(2);

        ImportApplyResponseDto applied = importApplyService.apply(
                event.getId(), preview.operationId(), corrected, ADMIN_USERNAME
        );
        assertThat(applied.retiredCount()).isEqualTo(4);
        assertThat(applied.insertedCount()).isEqualTo(2);
        assertThat(registrationRepository.count()).isEqualTo(6);
        assertThat(registrationRepository.countByRaceEventId(event.getId())).isEqualTo(2);
        for (Race race : races) {
            assertThat(registrationRepository.findAllCurrentByRaceIds(List.of(race.getId()))).hasSize(1);
            assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                    .isEqualTo(ResultsPublicationStatus.DRAFT);
        }
    }

    @Test
    void emergencyReplacementReturnsANoOpWithoutRevisionOrBatch() {
        Event event = createPublishedEvent("Stage F empty", "stage-f-empty");
        Long raceId = raceAdminService.create(
                event.getId(),
                new UpsertRaceRequest(
                        "empty", "Empty Race", "empty-race", null, null,
                        0, true
                ),
                ADMIN_USERNAME
        ).id();
        raceAdminService.create(
                event.getId(),
                new UpsertRaceRequest(
                        "5 km", "Outside Race", "outside-race", null, null,
                        1, true
                ),
                ADMIN_USERNAME
        );
        long revision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        byte[] empty = singleInquiryBibCsv("Вне", "finished").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "empty.csv", empty, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(raceId), 100, ADMIN_USERNAME
        );
        assertThat(preview.blockingErrorsPresent()).isFalse();
        assertThat(preview.emergencySummary().totals().currentCount()).isZero();
        assertThat(preview.emergencySummary().totals().sourceCount()).isZero();
        assertThat(preview.emergencySummary().totals().outOfScopeCount()).isOne();

        ImportApplyResponseDto noOp = importApplyService.apply(
                event.getId(), preview.operationId(), empty, ADMIN_USERNAME
        );
        assertThat(noOp.noOp()).isTrue();
        assertThat(noOp.status()).isEqualTo(ImportOperationStatus.PREVIEWED);
        assertThat(noOp.importBatchId()).isNull();
        assertThat(noOp.insertedCount()).isZero();
        assertThat(noOp.retiredCount()).isZero();
        assertThat(noOp.outOfScopeCount()).isOne();
        assertThat(noOp.newRevision()).isEqualTo(revision);
        assertThat(noOp.appliedAt()).isNull();
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision()).isEqualTo(revision);
        assertThat(importBatchRepository.count()).isZero();
        assertThat(registrationRepository.countByRaceEventId(event.getId())).isZero();
        assertThat(resultRepository.countByRegistrationRaceEventId(event.getId())).isZero();
        assertThat(importOperationRepository.findById(preview.operationId()).orElseThrow().getStatus())
                .isEqualTo(ImportOperationStatus.PREVIEWED);
    }

    @Test
    void emergencyReplacementRollsBackArchiveRetirementInsertAndRevisionTogether() {
        Event event = createPublishedEvent("Stage F rollback", "stage-f-rollback");
        importPublishedFixture(
                event.getId(), "old.csv",
                singleInquiryBibCsv("Старый", "finished").getBytes(StandardCharsets.UTF_8)
        );
        Race race = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId()).getFirst();
        raceResultsPublicationService.draft(event.getId(), race.getId(), "Rollback test", ADMIN_USERNAME);
        Registration oldRegistration = registrationRepository
                .findAllByRaceEventIdAndBib(event.getId(), "1100").getFirst();
        Result oldResult = resultRepository.findByRegistrationId(oldRegistration.getId()).orElseThrow();
        ResultIssueRequest issue = resultIssue(
                event, oldRegistration, oldResult,
                ResultIssueType.RESULT_CORRECTION, ResultCorrectionReason.OFFICIAL_TIME, "finished"
        );
        byte[] corrected = singleInquiryBibCsv("Новый", "finished").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "correct.csv", corrected, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        long revision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        long batches = importBatchRepository.count();

        ImportOperation operation = importOperationRepository.findById(preview.operationId()).orElseThrow();
        ImportOperationItem conflict = new ImportOperationItem();
        conflict.setOperation(operation);
        conflict.setSourceRowNumber(2);
        conflict.setBib("preexisting-audit-item");
        conflict.setDecision(ru.sportsresults.importing.ImportPreviewDecision.NEW);
        conflict.setAction(ru.sportsresults.importing.ImportPreviewAction.SKIP);
        conflict.setTargetRace(race);
        importOperationItemRepository.saveAndFlush(conflict);

        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), preview.operationId(), corrected, ADMIN_USERNAME
        )).isInstanceOf(RuntimeException.class);
        assertThat(registrationRepository.findById(oldRegistration.getId()).orElseThrow().isCurrent()).isTrue();
        assertThat(registrationRepository.count()).isOne();
        assertThat(resultRepository.count()).isOne();
        assertThat(resultIssueRequestRepository.findById(issue.getId()).orElseThrow().getQueueArchivedAt()).isNull();
        assertThat(resultIssueHistoryRepository.countByIssueRequest_IdAndAction(
                issue.getId(), ResultIssueHistoryAction.QUEUE_ARCHIVED)).isZero();
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision()).isEqualTo(revision);
        assertThat(importBatchRepository.count()).isEqualTo(batches);
        assertThat(importOperationRepository.findById(preview.operationId()).orElseThrow().getStatus())
                .isEqualTo(ImportOperationStatus.PREVIEWED);
        assertThat(importOperationItemRepository.findAllByOperationIdOrderBySourceRowNumberAsc(preview.operationId()))
                .singleElement();
    }

    @Test
    void emergencyReplacementSerializesConcurrentApplyOfTheSameOperation() throws Exception {
        Event event = createPublishedEvent("Stage F concurrency", "stage-f-concurrency");
        importPublishedFixture(
                event.getId(), "old.csv",
                singleInquiryBibCsv("Старый", "finished").getBytes(StandardCharsets.UTF_8)
        );
        Race race = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId()).getFirst();
        raceResultsPublicationService.draft(event.getId(), race.getId(), "Concurrency", ADMIN_USERNAME);
        byte[] corrected = singleInquiryBibCsv("Новый", "finished").getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), "correct.csv", corrected, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );

        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ImportApplyResponseDto> apply = () -> {
            start.await(10, TimeUnit.SECONDS);
            return importApplyService.apply(event.getId(), preview.operationId(), corrected, ADMIN_USERNAME);
        };
        var first = pool.submit(apply);
        var second = pool.submit(apply);
        start.countDown();
        try {
            ImportApplyResponseDto firstResponse = first.get(30, TimeUnit.SECONDS);
            ImportApplyResponseDto secondResponse = second.get(30, TimeUnit.SECONDS);
            assertThat(firstResponse).isEqualTo(secondResponse);
            assertThat(firstResponse.retiredCount()).isOne();
            assertThat(firstResponse.insertedCount()).isOne();
            assertThat(registrationRepository.count()).isEqualTo(2);
            assertThat(registrationRepository.countByRaceEventId(event.getId())).isOne();
            assertThat(importOperationItemRepository
                    .findAllByOperationIdOrderBySourceRowNumberAsc(preview.operationId())).hasSize(2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void safelyRecalculatesPastEventCategoriesWithoutChangingSourceFactsClustersOrIssues() throws Exception {
        StageF5Fixture fixture = stageF5Fixture("stage-f5-safe");
        Race race = fixture.race();

        UpsertCategoryRequest cosmetic = categoryRequest(
                fixture.adultThirty().getSourceName(), "Adults 30-39", 30, 39,
                CategoryGender.MALE, fixture.adultThirty().getDisplayOrder(), true
        );
        long revisionBeforeCosmeticChange = eventRepository.findById(fixture.event().getId()).orElseThrow()
                .getResultDataRevision();
        categoryAdminService.update(
                race.getId(), fixture.adultThirty().getId(), cosmetic, ADMIN_USERNAME
        );
        assertThat(raceRepository.findById(race.getId()).orElseThrow().isResultRecalculationRequired()).isFalse();
        assertThat(eventRepository.findById(fixture.event().getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revisionBeforeCosmeticChange);

        assertThatThrownBy(() -> rawAwardPolicyService.upsert(
                race.getId(), policy(AgeCalculationMode.END_OF_EVENT_YEAR), ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("RACE_RESULTS_MUST_BE_DRAFT");

        raceResultsPublicationService.draft(
                fixture.event().getId(), race.getId(), "Stage F.5 settings", ADMIN_USERNAME
        );
        categoryAdminService.update(
                race.getId(), fixture.adultThirty().getId(),
                categoryRequest("ADULT_30", "Adults 30-34", 30, 34, CategoryGender.MALE, 3, true),
                ADMIN_USERNAME
        );
        Category adultThirtyFive = categoryRepository.findById(categoryAdminService.create(
                race.getId(),
                categoryRequest("ADULT_35", "Adults 35-39", 35, 39, CategoryGender.MALE, 5, true),
                ADMIN_USERNAME
        ).id()).orElseThrow();

        assertThatThrownBy(() -> raceResultsPublicationService.publish(
                fixture.event().getId(), race.getId(), ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("RESULT_RECALCULATION_REQUIRED");

        Registration adult = registrationRepository.findById(fixture.adultRegistrationId()).orElseThrow();
        assertThat(adult.getCategory().getId()).isEqualTo(fixture.adultThirty().getId());
        long revisionBeforeApply = eventRepository.findById(fixture.event().getId()).orElseThrow()
                .getResultDataRevision();
        Map<String, Object> issueSnapshot = resultIssueSnapshotState(fixture.issueId());

        ResultRecalculationPreviewDto preview = resultRecalculationService.preview(
                fixture.event().getId(), List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(preview.currentRegistrationCount()).isEqualTo(4);
        assertThat(preview.minorCount()).isEqualTo(2);
        assertThat(preview.adultCount()).isEqualTo(2);
        assertThat(preview.noBirthDateCount()).isZero();
        assertThat(preview.minorSourceCategoryCount()).isEqualTo(2);
        assertThat(preview.adultCalculatedCategoryCount()).isEqualTo(2);
        assertThat(preview.adultWithoutCategoryCount()).isZero();
        assertThat(preview.changedCategoryCount()).isOne();
        assertThat(preview.unchangedCategoryCount()).isEqualTo(3);
        assertThat(preview.blockingCount()).isZero();
        assertThat(preview.rankingAffected()).isTrue();
        assertThat(preview.rows()).extracting(ResultRecalculationPreviewDto.Row::reason)
                .contains("UNCHANGED", "ADULT_RECALCULATED");

        ResultRecalculationApplyDto applied = resultRecalculationService.apply(
                fixture.event().getId(), preview.operationId(), ADMIN_USERNAME
        );
        assertThat(applied.changedCategoryCount()).isOne();
        assertThat(applied.newRevision()).isEqualTo(revisionBeforeApply + 1);
        assertThat(resultRecalculationService.apply(
                fixture.event().getId(), preview.operationId(), ADMIN_USERNAME
        )).isEqualTo(applied);

        Registration changed = registrationRepository.findById(fixture.adultRegistrationId()).orElseThrow();
        assertThat(changed.getCategory().getId()).isEqualTo(adultThirtyFive.getId());
        assertThat(changed.getBib()).isEqualTo("D-35");
        assertThat(changed.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
        assertThat(changed.getSourceCategory()).isEqualTo("SOURCE_ADULT");
        assertThat(changed.getCluster().getId()).isEqualTo(fixture.clusterTwoId());
        assertThat(registrationRepository.findById(fixture.retiredRegistrationId()).orElseThrow()
                .getCategory().getId()).isEqualTo(fixture.adultThirty().getId());
        assertThat(resultRepository.findByRegistrationId(changed.getId()).orElseThrow().getGunTime())
                .isEqualTo(Duration.ofSeconds(104));
        assertThat(resultIssueSnapshotState(fixture.issueId())).isEqualTo(issueSnapshot);
        assertThat(resultIssueRequestRepository.findById(fixture.issueId()).orElseThrow().getStatus())
                .isEqualTo(ResultIssueStatus.NEW);
        var recalculatedJournalDetail = context.getBean(ru.sportsresults.service.GlobalResultIssueJournalService.class)
                .get(fixture.issueId());
        assertThat(recalculatedJournalDetail.historicalSnapshot().effectiveCategoryName())
                .isEqualTo("Adults 30-39");
        assertThat(recalculatedJournalDetail.currentContext().category().name())
                .isEqualTo("Adults 35-39");
        assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.DRAFT);
        assertThat(raceRepository.findById(race.getId()).orElseThrow().isResultRecalculationRequired()).isFalse();

        raceResultsPublicationService.publish(fixture.event().getId(), race.getId(), ADMIN_USERNAME);
        assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.PUBLISHED);
        var publicResult = resultQueryService.search(
                fixture.event().getId(), race.getId(), null, "D-35", null, null, null,
                0, 20, "gunTime", "asc"
        );
        assertThat(publicResult.content()).singleElement().satisfies(item -> {
            assertThat(item.category().name()).isEqualTo("Adults 35-39");
            assertThat(item.rankingAchievements()).isNotEmpty();
        });

        raceResultsPublicationService.draft(
                fixture.event().getId(), race.getId(), "Concurrency", ADMIN_USERNAME
        );
        rawAwardPolicyService.upsert(
                race.getId(), policy(AgeCalculationMode.END_OF_EVENT_YEAR), ADMIN_USERNAME
        );
        ResultRecalculationPreviewDto first = resultRecalculationService.preview(
                fixture.event().getId(), List.of(race.getId()), 10, ADMIN_USERNAME
        );
        ResultRecalculationPreviewDto competing = resultRecalculationService.preview(
                fixture.event().getId(), List.of(race.getId()), 10, ADMIN_USERNAME
        );
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<ResultRecalculationApplyDto> concurrentApply = () -> {
            start.await(10, TimeUnit.SECONDS);
            return resultRecalculationService.apply(
                    fixture.event().getId(), first.operationId(), ADMIN_USERNAME
            );
        };
        var firstFuture = pool.submit(concurrentApply);
        var secondFuture = pool.submit(concurrentApply);
        start.countDown();
        try {
            assertThat(firstFuture.get(30, TimeUnit.SECONDS))
                    .isEqualTo(secondFuture.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertThatThrownBy(() -> resultRecalculationService.apply(
                fixture.event().getId(), competing.operationId(), ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("RECALC_PREVIEW_STALE");
    }

    @Test
    void eventDateChangeSwitchesMinorAndAdultOnlyAfterExplicitApply() {
        StageF5Fixture fixture = stageF5Fixture("stage-f5-date");
        Event event = eventRepository.findById(fixture.event().getId()).orElseThrow();
        UpdateEventRequest changedDate = new UpdateEventRequest(
                event.getEventSeries().getId(),
                event.getName(),
                event.getSlug(),
                Instant.parse("2025-06-16T08:00:00Z"),
                event.getEndsAt(),
                event.getLocation(),
                event.getTimeZone(),
                event.getPublicationStatus()
        );
        assertThatThrownBy(() -> eventService.updateEvent(event.getId(), changedDate, ADMIN_USERNAME))
                .isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("RACE_RESULTS_MUST_BE_DRAFT");

        raceResultsPublicationService.draft(
                fixture.event().getId(), fixture.race().getId(), "Date correction", ADMIN_USERNAME
        );
        Registration boundary = registrationRepository.findById(fixture.boundaryRegistrationId()).orElseThrow();
        assertThat(boundary.getCategory().getSourceName()).isEqualTo("CHILD_17");

        eventService.updateEvent(event.getId(), changedDate, ADMIN_USERNAME);

        assertThat(registrationRepository.findById(boundary.getId()).orElseThrow().getCategory().getSourceName())
                .isEqualTo("CHILD_17");
        ResultRecalculationPreviewDto preview = resultRecalculationService.preview(
                event.getId(), List.of(fixture.race().getId()), 100, ADMIN_USERNAME
        );
        assertThat(preview.minorCount()).isOne();
        assertThat(preview.adultCount()).isEqualTo(3);
        assertThat(preview.changedCategoryCount()).isOne();
        resultRecalculationService.apply(event.getId(), preview.operationId(), ADMIN_USERNAME);
        assertThat(registrationRepository.findById(boundary.getId()).orElseThrow().getCategory().getSourceName())
                .isEqualTo("ADULT_18");

        Event changedEvent = eventRepository.findById(event.getId()).orElseThrow();
        eventService.updateEvent(event.getId(), new UpdateEventRequest(
                changedEvent.getEventSeries().getId(),
                changedEvent.getName(),
                changedEvent.getSlug(),
                Instant.parse("2025-06-15T08:00:00Z"),
                changedEvent.getEndsAt(),
                changedEvent.getLocation(),
                changedEvent.getTimeZone(),
                changedEvent.getPublicationStatus()
        ), ADMIN_USERNAME);
        ResultRecalculationPreviewDto reverse = resultRecalculationService.preview(
                event.getId(), List.of(fixture.race().getId()), 100, ADMIN_USERNAME
        );
        resultRecalculationService.apply(event.getId(), reverse.operationId(), ADMIN_USERNAME);
        assertThat(registrationRepository.findById(boundary.getId()).orElseThrow().getCategory().getSourceName())
                .isEqualTo("CHILD_17");
    }

    @Test
    void recalculationRollsBackAtomicallyAndRejectsPreviewAfterConfigurationChanges() {
        StageF5Fixture fixture = stageF5Fixture("stage-f5-rollback");
        raceResultsPublicationService.draft(
                fixture.event().getId(), fixture.race().getId(), "Rollback", ADMIN_USERNAME
        );
        categoryAdminService.update(
                fixture.race().getId(), fixture.adultThirty().getId(),
                categoryRequest("ADULT_30", "Adults 30-34", 30, 34, CategoryGender.MALE, 3, true),
                ADMIN_USERNAME
        );
        categoryAdminService.create(
                fixture.race().getId(),
                categoryRequest("ADULT_35", "Adults 35-39", 35, 39, CategoryGender.MALE, 5, true),
                ADMIN_USERNAME
        );
        ResultRecalculationPreviewDto preview = resultRecalculationService.preview(
                fixture.event().getId(), List.of(fixture.race().getId()), 10, ADMIN_USERNAME
        );
        long revision = eventRepository.findById(fixture.event().getId()).orElseThrow()
                .getResultDataRevision();
        jdbcTemplate.execute("""
                CREATE FUNCTION stage_f5_recalculation_failure() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.id = %d AND NEW.category_id IS DISTINCT FROM OLD.category_id THEN
                        RAISE EXCEPTION 'forced Stage F.5 recalculation failure';
                    END IF;
                    RETURN NEW;
                END
                $$
                """.formatted(fixture.adultRegistrationId()));
        jdbcTemplate.execute("""
                CREATE TRIGGER stage_f5_recalculation_failure_trigger
                BEFORE UPDATE ON registrations
                FOR EACH ROW EXECUTE FUNCTION stage_f5_recalculation_failure()
                """);
        try {
            assertThatThrownBy(() -> resultRecalculationService.apply(
                    fixture.event().getId(), preview.operationId(), ADMIN_USERNAME
            )).isInstanceOf(RuntimeException.class)
                    .hasStackTraceContaining("forced Stage F.5 recalculation failure");
        } finally {
            jdbcTemplate.execute("DROP TRIGGER stage_f5_recalculation_failure_trigger ON registrations");
            jdbcTemplate.execute("DROP FUNCTION stage_f5_recalculation_failure()");
        }
        assertThat(registrationRepository.findById(fixture.adultRegistrationId()).orElseThrow()
                .getCategory().getId()).isEqualTo(fixture.adultThirty().getId());
        assertThat(eventRepository.findById(fixture.event().getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revision);
        assertThat(raceRepository.findById(fixture.race().getId()).orElseThrow()
                .isResultRecalculationRequired()).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM result_recalculation_operations WHERE id=?",
                String.class,
                preview.operationId()
        )).isEqualTo("PREVIEWED");

        Category configured = categoryRepository.findById(fixture.adultThirty().getId()).orElseThrow();
        categoryAdminService.update(
                fixture.race().getId(), configured.getId(),
                categoryRequest(
                        configured.getSourceName(), "Cosmetic rename after preview",
                        configured.getMinAge(), configured.getMaxAge(), configured.getGender(),
                        configured.getDisplayOrder(), configured.isEnabled()
                ),
                ADMIN_USERNAME
        );
        assertThatThrownBy(() -> resultRecalculationService.apply(
                fixture.event().getId(), preview.operationId(), ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("RECALC_PREVIEW_STALE");
        assertThat(registrationRepository.findById(fixture.adultRegistrationId()).orElseThrow()
                .getCategory().getId()).isEqualTo(fixture.adultThirty().getId());
    }

    @Test
    void importPreviewBlocksMissingMinorSourceAndPreservesAbsentUpdateSourceCategory() {
        Event event = createPublishedEvent("Stage F.5 import", "stage-f5-import");
        byte[] initial = (csvHeader() + """
                Ребёнок,Тест,male,2012-01-01,5 km,M-1,CHILD,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """).getBytes(StandardCharsets.UTF_8);
        assertThat(importPublishedFixture(event.getId(), "minor-initial.csv", initial).status())
                .isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(event.getId()).getFirst();

        byte[] missing = (csvHeader() + """
                Другой,Ребёнок,male,2012-01-01,5 km,M-2,,finished,1100.0,1000.0,2.0,2.0,,2.0,2.0,
                """).getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto blocked = importPreviewService.preview(
                event.getId(), "minor-missing.csv", missing, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(blocked.blockingErrorsPresent()).isTrue();
        assertThat(blocked.rows()).singleElement().satisfies(row -> {
            assertThat(row.reasonCode()).isEqualTo("MINOR_SOURCE_CATEGORY_REQUIRED");
            assertThat(row.futureAction()).isEqualTo(ru.sportsresults.importing.ImportPreviewAction.BLOCKED);
        });

        byte[] validAdd = (csvHeader() + """
                Второй,Ребёнок,male,2011-01-01,5 km,M-2,CHILD_2,finished,1100.0,1000.0,2.0,2.0,2.0,2.0,2.0,2.0
                """).getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto add = importPreviewService.preview(
                event.getId(), "minor-add.csv", validAdd, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(add.blockingErrorsPresent()).isFalse();
        importApplyService.apply(event.getId(), add.operationId(), validAdd, ADMIN_USERNAME);
        assertThat(registrationRepository.findAllByRaceIdAndBib(race.getId(), "M-2").getFirst()
                .getCategory().getSourceName()).isEqualTo("CHILD_2");

        String absentCategoryCsv = """
                name,surname,gender,birthdate,event,dorsal,status,times.official_:::finish:::
                Ребёнок,Обновлён,male,2012-01-01,5 km,M-1,finished,1200.0
                """;
        byte[] absent = absentCategoryCsv.getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto update = importPreviewService.preview(
                event.getId(), "minor-absent.csv", absent, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(update.blockingErrorsPresent()).isFalse();
        importApplyService.apply(event.getId(), update.operationId(), absent, ADMIN_USERNAME);
        Registration registration = registrationRepository.findAllByRaceIdAndBib(race.getId(), "M-1").getFirst();
        assertThat(registration.getSourceCategory()).isEqualTo("CHILD");
        assertThat(registration.getCategory().getSourceName()).isEqualTo("CHILD");

        byte[] emptyCategory = (csvHeader() + """
                Ребёнок,Пусто,male,2012-01-01,5 km,M-1,,finished,1300.0,1200.0,1.0,1.0,,1.0,1.0,
                """).getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto emptyUpdate = importPreviewService.preview(
                event.getId(), "minor-empty.csv", emptyCategory, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(emptyUpdate.blockingErrorsPresent()).isTrue();
        assertThat(emptyUpdate.rows().getFirst().reasonCode()).isEqualTo("MINOR_SOURCE_CATEGORY_REQUIRED");

        raceResultsPublicationService.draft(event.getId(), race.getId(), "Emergency minor", ADMIN_USERNAME);
        ImportPreviewResponseDto emergencyBlocked = importPreviewService.preview(
                event.getId(), "minor-emergency-missing.csv", missing, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(emergencyBlocked.blockingErrorsPresent()).isTrue();
        assertThat(emergencyBlocked.rows().getFirst().reasonCode())
                .isEqualTo("MINOR_SOURCE_CATEGORY_REQUIRED");

        byte[] validEmergency = (csvHeader() + """
                Новый,Ребёнок,male,2013-01-01,5 km,M-3,CHILD_3,finished,1400.0,1300.0,1.0,1.0,1.0,1.0,1.0,1.0
                """).getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto emergency = importPreviewService.preview(
                event.getId(), "minor-emergency.csv", validEmergency, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(emergency.blockingErrorsPresent()).isFalse();
        importApplyService.apply(event.getId(), emergency.operationId(), validEmergency, ADMIN_USERNAME);
        assertThat(registrationRepository.findAllByRaceIdAndBib(race.getId(), "M-3").getFirst()
                .getCategory().getSourceName()).isEqualTo("CHILD_3");
        assertThat(registrationRepository.countByRaceEventId(event.getId())).isOne();
    }

    @Test
    void categoryConfigurationChangeInvalidatesEveryImportModePreview() {
        StageF5Fixture fixture = stageF5Fixture("stage-f5-import-stale");
        Race race = fixture.race();
        raceResultsPublicationService.draft(
                fixture.event().getId(), race.getId(), "Import staleness", ADMIN_USERNAME
        );

        byte[] add = (csvHeader() + """
                Новый,Взрослый,male,1991-01-01,5 km,NEW-ADULT,,finished,1100.0,1000.0,5.0,5.0,,5.0,5.0,
                """).getBytes(StandardCharsets.UTF_8);
        byte[] update = (csvHeader() + """
                Runner,Обновлён,male,1990-01-01,5 km,D-35,SOURCE_ADULT,finished,1200.0,1100.0,4.0,4.0,,4.0,4.0,
                """).getBytes(StandardCharsets.UTF_8);
        byte[] emergency = (csvHeader() + """
                Новый,Ребёнок,male,2013-01-01,5 km,NEW-MINOR,CHILD_12,finished,1300.0,1200.0,1.0,1.0,1.0,1.0,1.0,1.0
                """).getBytes(StandardCharsets.UTF_8);

        ImportPreviewResponseDto addPreview = importPreviewService.preview(
                fixture.event().getId(), "stale-add.csv", add, ImportOperationMode.ADD_NEW,
                List.of(race.getId()), 10, ADMIN_USERNAME
        );
        ImportPreviewResponseDto updatePreview = importPreviewService.preview(
                fixture.event().getId(), "stale-update.csv", update, ImportOperationMode.UPDATE_EXISTING,
                List.of(race.getId()), 10, ADMIN_USERNAME
        );
        ImportPreviewResponseDto emergencyPreview = importPreviewService.preview(
                fixture.event().getId(), "stale-emergency.csv", emergency, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 10, ADMIN_USERNAME
        );
        assertThat(addPreview.blockingErrorsPresent()).isFalse();
        assertThat(updatePreview.blockingErrorsPresent()).isFalse();
        assertThat(emergencyPreview.blockingErrorsPresent()).isFalse();

        Category category = categoryRepository.findById(fixture.adultThirty().getId()).orElseThrow();
        categoryAdminService.update(
                race.getId(), category.getId(),
                categoryRequest(
                        category.getSourceName(), category.getDisplayName(), category.getMinAge(), 38,
                        category.getGender(), category.getDisplayOrder(), category.isEnabled()
                ),
                ADMIN_USERNAME
        );

        assertImportPreviewStale(fixture.event().getId(), addPreview.operationId(), add);
        assertImportPreviewStale(fixture.event().getId(), updatePreview.operationId(), update);
        assertImportPreviewStale(fixture.event().getId(), emergencyPreview.operationId(), emergency);
        assertThat(registrationRepository.countByRaceEventId(fixture.event().getId())).isEqualTo(4);
        assertThat(registrationRepository.findAllByRaceIdAndBib(race.getId(), "NEW-ADULT")).isEmpty();
        assertThat(registrationRepository.findAllByRaceIdAndBib(race.getId(), "NEW-MINOR")).isEmpty();
    }

    @Test
    void emergencyReplacementThenRecalculationKeepsRetiredGenerationHistorical() {
        Event event = createPublishedEvent("Stage F.5 emergency recalc", "stage-f5-emergency-recalc");
        byte[] oldDataset = (csvHeader() + """
                Старый,Участник,male,1990-01-01,5 km,OLD-1,LEGACY_ADULT,finished,1000.0,900.0,1.0,1.0,,1.0,1.0,
                """).getBytes(StandardCharsets.UTF_8);
        assertThat(importPublishedFixture(event.getId(), "old-generation.csv", oldDataset).status())
                .isEqualTo(ImportBatchStatus.SUCCEEDED);
        Race race = raceRepository.findByEventIdAndSourceCode(event.getId(), "5 km").orElseThrow();
        Registration oldRegistration = registrationRepository.findAllByRaceIdAndBib(race.getId(), "OLD-1")
                .getFirst();
        Long oldRegistrationId = oldRegistration.getId();
        Long oldResultId = resultRepository.findByRegistrationId(oldRegistrationId).orElseThrow().getId();
        Long oldCategoryId = oldRegistration.getCategory() == null ? null : oldRegistration.getCategory().getId();

        raceResultsPublicationService.draft(event.getId(), race.getId(), "Correct source dataset", ADMIN_USERNAME);
        Category adultRange = categoryRepository.findById(categoryAdminService.create(
                race.getId(),
                categoryRequest("ADULT_18", "Adults 18-39", 18, 39, CategoryGender.MALE, 10, true),
                ADMIN_USERNAME
        ).id()).orElseThrow();

        byte[] replacement = (csvHeader() + """
                Новый,Ребёнок,male,2015-01-01,5 km,CHILD-1,CHILD_12,finished,1100.0,1000.0,1.0,1.0,1.0,1.0,1.0,1.0
                Новый,Взрослый,male,1990-01-01,5 km,ADULT-1,SOURCE_ADULT,finished,1200.0,1100.0,2.0,2.0,1.0,2.0,2.0,1.0
                """).getBytes(StandardCharsets.UTF_8);
        ImportPreviewResponseDto replacementPreview = importPreviewService.preview(
                event.getId(), "correct-generation.csv", replacement, ImportOperationMode.EMERGENCY_REPLACE,
                List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(replacementPreview.blockingErrorsPresent()).isFalse();
        importApplyService.apply(event.getId(), replacementPreview.operationId(), replacement, ADMIN_USERNAME);

        Registration retired = registrationRepository.findById(oldRegistrationId).orElseThrow();
        assertThat(retired.isCurrent()).isFalse();
        assertThat(retired.getCategory() == null ? null : retired.getCategory().getId()).isEqualTo(oldCategoryId);
        assertThat(resultRepository.findByRegistrationId(oldRegistrationId).orElseThrow().getId())
                .isEqualTo(oldResultId);
        Registration minor = registrationRepository.findAllByRaceIdAndBib(race.getId(), "CHILD-1").getFirst();
        Registration adult = registrationRepository.findAllByRaceIdAndBib(race.getId(), "ADULT-1").getFirst();
        assertThat(minor.getCategory().getSourceName()).isEqualTo("CHILD_12");
        assertThat(adult.getCategory().getId()).isEqualTo(adultRange.getId());
        assertThat(minor.getId()).isNotEqualTo(oldRegistrationId);
        assertThat(resultRepository.findByRegistrationId(minor.getId()).orElseThrow().getId())
                .isNotEqualTo(oldResultId);

        categoryAdminService.update(
                race.getId(), adultRange.getId(),
                categoryRequest("ADULT_18", "Adults 18-34", 18, 34, CategoryGender.MALE, 10, true),
                ADMIN_USERNAME
        );
        Category adultThirtyFive = categoryRepository.findById(categoryAdminService.create(
                race.getId(),
                categoryRequest("ADULT_35", "Adults 35-44", 35, 44, CategoryGender.MALE, 11, true),
                ADMIN_USERNAME
        ).id()).orElseThrow();
        ResultRecalculationPreviewDto recalculation = resultRecalculationService.preview(
                event.getId(), List.of(race.getId()), 100, ADMIN_USERNAME
        );
        assertThat(recalculation.currentRegistrationCount()).isEqualTo(2);
        assertThat(recalculation.minorCount()).isOne();
        assertThat(recalculation.adultCount()).isOne();
        assertThat(recalculation.changedCategoryCount()).isOne();
        resultRecalculationService.apply(event.getId(), recalculation.operationId(), ADMIN_USERNAME);

        assertThat(registrationRepository.findById(minor.getId()).orElseThrow().getCategory().getSourceName())
                .isEqualTo("CHILD_12");
        assertThat(registrationRepository.findById(adult.getId()).orElseThrow().getCategory().getId())
                .isEqualTo(adultThirtyFive.getId());
        assertThat(registrationRepository.findById(oldRegistrationId).orElseThrow().isCurrent()).isFalse();
        assertThat(registrationRepository.findById(oldRegistrationId).orElseThrow().getCategory() == null
                ? null : registrationRepository.findById(oldRegistrationId).orElseThrow().getCategory().getId())
                .isEqualTo(oldCategoryId);
        assertThat(raceRepository.findById(race.getId()).orElseThrow().getResultsPublicationStatus())
                .isEqualTo(ResultsPublicationStatus.DRAFT);
    }

    @Test
    void globalResultIssueJournalIsAdminOnlyGlobalAllByDefaultAndKeepsEventQueueSemantics() throws Exception {
        AdminIssueFixture kazan = createGlobalIssueFixture(
                "Global Kazan", "global-kazan", "Kazan", "2026-05-17T06:00:00Z", 6
        );
        AdminIssueFixture moscow = createGlobalIssueFixture(
                "Global Moscow", "global-moscow", "Moscow", "2026-06-21T06:00:00Z", 2
        );
        ResultIssueRequest archivedNew = kazan.issues().getFirst();
        resultIssueLifecycleService.archiveManual(
                kazan.event().getId(), archivedNew.getId(), ResultIssueArchiveReason.MANUAL, ADMIN_USERNAME
        );
        long kazanRevision = eventRepository.findById(kazan.event().getId()).orElseThrow()
                .getResultDataRevision();
        String globalUrl = "/api/admin/result-issue-requests";

        mockMvc.perform(get(globalUrl)).andExpect(status().isUnauthorized());
        String body = mockMvc.perform(get(globalUrl)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(8))
                .andExpect(jsonPath("$.sort").value("createdAt"))
                .andExpect(jsonPath("$.direction").value("desc"))
                .andExpect(jsonPath("$.content[0].issueId").value(moscow.issues().getLast().getId()))
                .andExpect(jsonPath("$.content[0].event.eventName").value("Global Moscow"))
                .andExpect(jsonPath("$.content[0].event.location").value("Moscow"))
                .andExpect(jsonPath("$.content[0].snapshotOrigin").value("CAPTURED_AT_CREATION"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("contactEmail", "message", "rankingAchievements", "storageKey");

        mockMvc.perform(get(globalUrl)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("queueScope", "ARCHIVED")
                        .param("queueArchiveReason", "MANUAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].issueId").value(archivedNew.getId()))
                .andExpect(jsonPath("$.content[0].status").value("NEW"))
                .andExpect(jsonPath("$.content[0].queueArchiveReason").value("MANUAL"));
        mockMvc.perform(get(globalUrl)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("queueScope", "CURRENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(7));
        mockMvc.perform(get(globalUrl)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                .param("queueScope", "CURRENT")
                        .param("status", "NEW", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));

        mockMvc.perform(get("/api/admin/events/{eventId}/result-issue-requests", kazan.event().getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));
        assertThat(eventRepository.findById(kazan.event().getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(kazanRevision);
    }

    @Test
    void globalJournalUsesHistoricalSnapshotAndSeparatesRetiredCurrentContext() throws Exception {
        AdminIssueFixture fixture = createGlobalIssueFixture(
                "Historical Event", "historical-event", "Kazan", "2026-04-12T06:00:00Z", 3
        );
        ResultIssueRequest issue = fixture.issues().getFirst();
        Registration registration = fixture.registrations().getFirst();
        Result currentResult = resultRepository.findByRegistrationId(registration.getId()).orElseThrow();
        Race oldRace = registration.getRace();
        Category oldCategory = registration.getCategory();
        context.getBean(ru.sportsresults.service.ResultIssueHistoryService.class)
                .created(issue, "public", issue.getCreatedAt());
        adminResultIssueService.updateStatus(
                fixture.event().getId(), issue.getId(),
                new ru.sportsresults.api.dto.UpdateResultIssueStatusRequest(
                        ResultIssueStatus.NEW, ResultIssueStatus.IN_PROGRESS, null
                ), ADMIN_USERNAME
        );
        resultIssueLifecycleService.archiveManual(
                fixture.event().getId(), issue.getId(), ResultIssueArchiveReason.MANUAL, ADMIN_USERNAME
        );
        createAdminAttachment(issue, "one.pdf", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.CLEAN);
        createAdminAttachment(issue, "two.jpg", AttachmentUploadStatus.PENDING_UPLOAD, AttachmentScanStatus.PENDING);
        createAdminAttachment(issue, "three.mp4", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.CLEAN);

        Race newRace = new Race();
        newRace.setEvent(fixture.event());
        newRace.setSourceCode("10 km updated");
        newRace.setName("10 km updated");
        newRace.setSlug("historical-event-10-km-updated");
        newRace.setDisplayOrder(10);
        newRace = raceRepository.saveAndFlush(newRace);
        Category newCategory = new Category();
        newCategory.setRace(newRace);
        newCategory.setSourceName("UPDATED");
        newCategory.setDisplayName("Updated current category");
        newCategory.setDisplayOrder(0);
        newCategory = categoryRepository.saveAndFlush(newCategory);
        UUID retirementOperationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO import_operations(
                    id, event_id, operation_mode, status, source_filename, file_sha256,
                    base_revision, plan_digest, created_by, preview_summary, created_at, updated_at
                ) VALUES (?, ?, 'EMERGENCY_REPLACE', 'PREVIEWED', 'journal-retire.csv', repeat('a', 64),
                          ?, repeat('b', 64), ?, '{}'::jsonb, now(), now())
                """, retirementOperationId, fixture.event().getId(),
                fixture.event().getResultDataRevision(), ADMIN_USERNAME);
        new org.springframework.transaction.support.TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class)
        ).executeWithoutResult(transactionStatus -> {
            ResultIssueRequest emergencyArchived = resultIssueRequestRepository.findByIdAndEventIdForUpdate(
                    fixture.issues().get(1).getId(), fixture.event().getId()
            ).orElseThrow();
            resultIssueLifecycleService.archive(
                    emergencyArchived,
                    ResultIssueArchiveReason.EMERGENCY_REPLACEMENT,
                    retirementOperationId,
                    ADMIN_USERNAME,
                    Instant.now()
            );
        });
        registration.setRace(newRace);
        registration.setCategory(newCategory);
        registration.setBib("NEW-001");
        registration.setDisplayName("Новый участник");
        registration.setRetiredAt(Instant.now());
        registration.setRetiredByImportOperationId(retirementOperationId);
        registrationRepository.saveAndFlush(registration);
        currentResult.setStatus("running");
        currentResult.setGunTime(Duration.ofMillis(9_999));
        currentResult.setChipTime(null);
        resultRepository.saveAndFlush(currentResult);
        Event currentEvent = fixture.event();
        currentEvent.setLocation("Moscow");
        currentEvent.setStartsAt(Instant.parse("2027-04-12T06:00:00Z"));
        eventRepository.saveAndFlush(currentEvent);
        jdbcTemplate.update(
                "UPDATE result_issue_requests SET snapshot_origin='LEGACY_BACKFILL_CURRENT_STATE' WHERE id=?",
                fixture.issues().get(1).getId()
        );

        String globalUrl = "/api/admin/result-issue-requests";
        mockMvc.perform(get(globalUrl)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("location", "kAz")
                        .param("eventDateFrom", "2026-04-12")
                        .param("eventDateTo", "2026-04-12")
                        .param("raceId", oldRace.getId().toString())
                        .param("bib", " Q-1 ")
                        .param("participant", "участник1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].issueId").value(issue.getId()))
                .andExpect(jsonPath("$.content[0].event.location").value("Kazan"))
                .andExpect(jsonPath("$.content[0].race.raceId").value(oldRace.getId()))
                .andExpect(jsonPath("$.content[0].participant.bib").value("Q-1"))
                .andExpect(jsonPath("$.content[0].categoryName").value(oldCategory.getDisplayName()))
                .andExpect(jsonPath("$.content[0].attachmentCount").value(3));
        mockMvc.perform(get(globalUrl)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("raceId", newRace.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(globalUrl)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("bib", "NEW-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get(globalUrl)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("issueId", fixture.issues().get(1).getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].snapshotOrigin").value("LEGACY_BACKFILL_CURRENT_STATE"));
        mockMvc.perform(get(globalUrl)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("queueArchiveReason", "EMERGENCY_REPLACEMENT")
                        .param("queueArchivedImportOperationId", retirementOperationId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].issueId").value(fixture.issues().get(1).getId()));

        String detailBody = mockMvc.perform(get("/api/admin/result-issue-requests/{issueId}", issue.getId())
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactEmail").value("runner1@example.org"))
                .andExpect(jsonPath("$.message").value("Проверка обращения 1"))
                .andExpect(jsonPath("$.historicalSnapshot.eventLocation").value("Kazan"))
                .andExpect(jsonPath("$.historicalSnapshot.raceId").value(oldRace.getId()))
                .andExpect(jsonPath("$.historicalSnapshot.bib").value("Q-1"))
                .andExpect(jsonPath("$.historicalSnapshot.observedResultStatus").doesNotExist())
                .andExpect(jsonPath("$.currentContext.registrationExists").value(true))
                .andExpect(jsonPath("$.currentContext.registrationRetired").value(true))
                .andExpect(jsonPath("$.currentContext.race.raceId").value(newRace.getId()))
                .andExpect(jsonPath("$.currentContext.category.name").value("Updated current category"))
                .andExpect(jsonPath("$.currentContext.result.status").value("running"))
                .andExpect(jsonPath("$.currentContext.result.gunTimeMs").value(9_999))
                .andExpect(jsonPath("$.attachmentCount").value(3))
                .andExpect(jsonPath("$.attachments.length()").value(3))
                .andExpect(jsonPath("$.history[0].action").value("CREATED"))
                .andExpect(jsonPath("$.history[1].action").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$.history[2].action").value("QUEUE_ARCHIVED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(detailBody).doesNotContain("storageKey", "downloadUrl", "binary");
    }

    @Test
    void globalJournalCombinesFiltersPaginatesDeterministicallyAndValidatesInputs() throws Exception {
        AdminIssueFixture kazan = createGlobalIssueFixture(
                "Combined Kazan", "combined-kazan", "Kazan", "2026-07-05T06:00:00Z", 6
        );
        AdminIssueFixture moscow = createGlobalIssueFixture(
                "Combined Moscow", "combined-moscow", "Moscow", "2025-07-05T06:00:00Z", 6
        );
        String url = "/api/admin/result-issue-requests";
        ResultIssueRequest target = kazan.issues().get(2);

        mockMvc.perform(get(url)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("location", "kazan")
                        .param("eventDateFrom", "2026-01-01")
                        .param("eventDateTo", "2026-12-31")
                        .param("createdFrom", "2020-01-01T00:00:00Z")
                        .param("createdTo", "2030-01-01T00:00:00Z")
                        .param("eventId", kazan.event().getId().toString())
                        .param("raceId", target.getSnapshotRaceId().toString())
                        .param("raceCode", target.getSnapshotRaceCode())
                        .param("issueType", "RESULT_CORRECTION")
                        .param("correctionReason", "OFFICIAL_TIME")
                        .param("status", "RESOLVED")
                        .param("queueScope", "CURRENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].issueId").value(target.getId()));

        mockMvc.perform(get(url)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].issueId").value(moscow.issues().getLast().getId()))
                .andExpect(jsonPath("$.content[4].issueId").value(moscow.issues().get(1).getId()));
        mockMvc.perform(get(url)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].issueId").value(moscow.issues().getFirst().getId()))
                .andExpect(jsonPath("$.content[1].issueId").value(kazan.issues().getLast().getId()));

        for (String sort : List.of("createdAt", "eventDate", "issueId", "status", "queueArchivedAt")) {
            mockMvc.perform(get(url)
                            .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                            .param("sort", sort).param("direction", "asc").param("size", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sort").value(sort))
                    .andExpect(jsonPath("$.direction").value("asc"));
        }
        mockMvc.perform(get(url).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)).param("sort", "message"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JOURNAL_SORT"));
        mockMvc.perform(get(url).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("eventDateFrom", "2027-01-01").param("eventDateTo", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EVENT_DATE_RANGE"));
        mockMvc.perform(get(url).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)).param("size", "201"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_SIZE"));
        mockMvc.perform(get(url).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)).param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(get(url).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)).param("location", "Nowhere"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void globalJournalIndexesSupportTenThousandRowQueryShapes() {
        AdminIssueFixture kazan = createGlobalIssueFixture(
                "Volume Kazan", "volume-kazan", "Kazan", "2026-08-01T06:00:00Z", 1
        );
        AdminIssueFixture moscow = createGlobalIssueFixture(
                "Volume Moscow", "volume-moscow", "Moscow", "2025-08-01T06:00:00Z", 1
        );
        insertSyntheticJournalRows(kazan, "K-", 100, 150);
        insertSyntheticJournalRows(moscow, "M-", 10_000, 10_050);
        jdbcTemplate.execute("ANALYZE result_issue_requests");

        String defaultPlan = explain("""
                SELECT id FROM result_issue_requests
                ORDER BY created_at DESC, id DESC LIMIT 50
                """);
        String eventPlan = explain("""
                SELECT id FROM result_issue_requests
                WHERE event_id=%d ORDER BY created_at DESC, id DESC LIMIT 50
                """.formatted(kazan.event().getId()));
        String bibPlan = explain("""
                SELECT id FROM result_issue_requests
                WHERE snapshot_bib='K-000249' ORDER BY created_at DESC, id DESC LIMIT 50
                """);
        String racePlan = explain("""
                SELECT id FROM result_issue_requests
                WHERE snapshot_race_id=%d ORDER BY created_at DESC, id DESC LIMIT 50
                """.formatted(kazan.registrations().getFirst().getRace().getId()));
        String eventDatePlan = explain("""
                SELECT id FROM result_issue_requests
                WHERE snapshot_event_starts_at >= TIMESTAMPTZ '2026-08-01T00:00:00Z'
                  AND snapshot_event_starts_at < TIMESTAMPTZ '2026-08-02T00:00:00Z'
                ORDER BY created_at DESC, id DESC LIMIT 50
                """);
        String workingPlan = explain("""
                SELECT id FROM result_issue_requests
                WHERE queue_archived_at IS NULL AND status='NEW'
                ORDER BY created_at DESC, id DESC LIMIT 50
                """);
        String workingCountPlan = explain("""
                SELECT count(*) FROM result_issue_requests
                WHERE queue_archived_at IS NULL AND status='NEW'
                """);

        assertThat(resultIssueRequestRepository.count()).isGreaterThan(10_000);
        assertThat(defaultPlan).contains("ix_result_issue_journal_created");
        assertThat(eventPlan).contains("ix_result_issue_journal_event_created");
        assertThat(bibPlan).contains("ix_result_issue_journal_bib_created");
        assertThat(racePlan).contains("ix_result_issue_journal_race_created");
        assertThat(eventDatePlan).contains("ix_result_issue_journal_event_date_created");
        assertThat(workingPlan).contains("ix_result_issue_journal_created");
        assertThat(workingCountPlan).contains("uk_result_issue_active_registration");
    }

    @Test
    void journalXlsxExportMatchesStageGFiltersAndCreatesSafeRevocableLinks() throws Exception {
        AdminIssueFixture kazan = createGlobalIssueFixture(
                "Экспорт Казань", "xlsx-kazan", "Казань", "2026-08-01T06:00:00Z", 6
        );
        createGlobalIssueFixture(
                "Export Moscow", "xlsx-moscow", "Moscow", "2025-08-01T06:00:00Z", 6
        );
        ResultIssueRequest linkedIssue = kazan.issues().getFirst();
        linkedIssue.setMessage("=1+" + "Я".repeat(3_997));
        resultIssueRequestRepository.saveAndFlush(linkedIssue);

        ResultIssueAttachment photo = createAdminAttachment(
                linkedIssue, "фото участника.jpg", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.CLEAN
        );
        photo.setContentType("image/jpeg");
        photo.setDetectedContentType("image/jpeg");
        photo = resultIssueAttachmentRepository.saveAndFlush(photo);
        ResultIssueAttachment video = createAdminAttachment(
                linkedIssue, "finish-video.mp4", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.CLEAN
        );
        ResultIssueAttachment pdf = createAdminAttachment(
                linkedIssue, "statement.pdf", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.CLEAN
        );
        createAdminAttachment(
                linkedIssue, "pending.jpg", AttachmentUploadStatus.PENDING_UPLOAD, AttachmentScanStatus.PENDING
        );
        createAdminAttachment(
                linkedIssue, "infected.pdf", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.INFECTED
        );
        createAdminAttachment(
                linkedIssue, "deleted.pdf", AttachmentUploadStatus.DELETED, AttachmentScanStatus.CLEAN
        );
        for (ResultIssueAttachment attachment : List.of(photo, video, pdf)) {
            attachmentObjectStorage.recordUploadedObject(
                    attachment.getStorageKey(), attachment.getSizeBytes(), attachment.getDetectedContentType(), "etag"
            );
        }

        Race race = kazan.registrations().getFirst().getRace();
        String journalBody = mockMvc.perform(get("/api/admin/result-issue-requests")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .param("location", "казань")
                        .param("raceId", race.getId().toString())
                        .param("status", "NEW", "IN_PROGRESS")
                        .param("queueScope", "CURRENT")
                        .param("sort", "issueId")
                        .param("direction", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andReturn().getResponse().getContentAsString();
        Set<Long> apiIssueIds = new HashSet<>();
        for (var node : objectMapper.readTree(journalBody).path("content")) {
            apiIssueIds.add(node.path("issueId").asLong());
        }

        Map<String, Object> exportRequest = new LinkedHashMap<>();
        exportRequest.put("location", "казань");
        exportRequest.put("raceId", race.getId());
        exportRequest.put("statuses", List.of("NEW", "IN_PROGRESS"));
        exportRequest.put("queueScope", "CURRENT");
        exportRequest.put("sort", "issueId");
        exportRequest.put("direction", "asc");
        exportRequest.put("shareLifetimeDays", 7);
        long sportsRevisionBefore = eventRepository.findById(kazan.event().getId()).orElseThrow()
                .getResultDataRevision();
        MvcResult export = performXlsxExport(exportRequest);
        byte[] xlsx = export.getResponse().getContentAsByteArray();
        String batchHeader = export.getResponse().getHeader("X-Share-Batch-Id");
        String expiryHeader = export.getResponse().getHeader("X-Share-Expires-At");
        assertThat(batchHeader).isNotBlank();
        assertThat(expiryHeader).isNotBlank();

        List<String> shareUrls = new ArrayList<>();
        Set<Long> workbookIssueIds = new HashSet<>();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(List.of(
                    workbook.getSheetName(0), workbook.getSheetName(1), workbook.getSheetName(2)
            )).containsExactly("Обращения", "Вложения", "Информация");
            Sheet issues = workbook.getSheet("Обращения");
            Sheet attachments = workbook.getSheet("Вложения");
            assertThat(issues.getLastRowNum()).isEqualTo(4);
            assertThat(attachments.getLastRowNum()).isEqualTo(6);
            assertThat(headerValues(issues)).contains(
                    "ID обращения", "Категория на момент обращения", "Snapshot origin",
                    "Текущий старт", "Текущая категория", "Registration retired"
            ).noneMatch(value -> value.toLowerCase().contains("рожд"));
            for (int rowIndex = 1; rowIndex <= issues.getLastRowNum(); rowIndex++) {
                workbookIssueIds.add((long) issues.getRow(rowIndex).getCell(0).getNumericCellValue());
                for (Cell cell : issues.getRow(rowIndex)) {
                    assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
                }
            }
            Row escapedRow = findRowByLongId(issues, linkedIssue.getId());
            assertThat(escapedRow.getCell(22).getStringCellValue())
                    .startsWith("'=1+")
                    .hasSize(4_001);
            assertThat(cellText(escapedRow.getCell(1))).isEqualTo("Экспорт Казань");

            int unavailable = 0;
            for (int rowIndex = 1; rowIndex <= attachments.getLastRowNum(); rowIndex++) {
                Cell link = attachments.getRow(rowIndex).getCell(11);
                if (link.getHyperlink() == null) {
                    unavailable++;
                    assertThat(cellText(link)).startsWith("Недоступно:");
                } else {
                    shareUrls.add(link.getHyperlink().getAddress());
                    assertThat(link.getHyperlink().getAddress())
                            .startsWith("https://results.test/share/attachments/")
                            .doesNotContain("object-storage", "storageKey", "authorization=");
                }
            }
            assertThat(unavailable).isEqualTo(3);
            assertThat(shareUrls).hasSize(3);
            assertThat(allWorkbookText(workbook))
                    .doesNotContain(photo.getStorageKey(), "token_hash", "storage_key", "Дата рождения");
            assertThat(findInformationValue(workbook.getSheet("Информация"), "Share Batch ID"))
                    .isEqualTo(batchHeader);
            assertThat(findInformationValue(workbook.getSheet("Информация"), "Количество обращений"))
                    .isEqualTo("4");
            assertThat(findInformationValue(workbook.getSheet("Информация"), "Количество созданных share links"))
                    .isEqualTo("3");
        }
        assertThat(workbookIssueIds).isEqualTo(apiIssueIds);

        UUID firstBatchId = UUID.fromString(batchHeader);
        assertThat(resultIssueShareBatchRepository.findById(firstBatchId).orElseThrow().getCreatedBy())
                .isEqualTo(ADMIN_USERNAME);
        assertThat(resultIssueAttachmentShareGrantRepository.countByBatch_Id(firstBatchId)).isEqualTo(3);
        List<String> rawTokens = tokens(shareUrls);
        List<String> databaseHashes = jdbcTemplate.queryForList(
                "SELECT token_hash FROM result_issue_attachment_share_grants WHERE batch_id=?",
                String.class,
                firstBatchId
        );
        assertThat(databaseHashes).hasSize(3).doesNotContainAnyElementsOf(rawTokens);
        assertThat(databaseHashes).containsExactlyInAnyOrderElementsOf(
                rawTokens.stream().map(resultIssueShareTokenService::hash).toList()
        );

        for (String token : rawTokens) {
            mockMvc.perform(get("/share/attachments/{token}", token))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                            "https://object-storage.invalid/download/"
                    )))
                    .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                            "response-content-disposition="
                    )))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"));
        }
        mockMvc.perform(get("/share/attachments/{token}", "A".repeat(43)))
                .andExpect(status().isNotFound());
        assertThat(resultIssueAttachmentShareGrantRepository
                .countByBatch_IdAndAccessCountGreaterThan(firstBatchId, 0)).isEqualTo(3);

        mockMvc.perform(get("/api/admin/result-issue-share-batches/{batchId}", firstBatchId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/result-issue-share-batches/{batchId}", firstBatchId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdBy").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.grantCount").value(3))
                .andExpect(jsonPath("$.accessedGrantCount").value(3));
        mockMvc.perform(post("/api/admin/result-issue-share-batches/{batchId}/revoke", firstBatchId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedBy").value(ADMIN_USERNAME));
        mockMvc.perform(post("/api/admin/result-issue-share-batches/{batchId}/revoke", firstBatchId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk());
        for (String token : rawTokens) {
            mockMvc.perform(get("/share/attachments/{token}", token)).andExpect(status().isNotFound());
        }

        MvcResult secondExport = performXlsxExport(exportRequest);
        List<String> secondUrls = workbookShareUrls(secondExport.getResponse().getContentAsByteArray());
        List<String> secondTokens = tokens(secondUrls);
        assertThat(secondTokens).hasSize(3).doesNotContainAnyElementsOf(rawTokens);
        ResultIssueAttachmentShareGrant individuallyRevoked = resultIssueAttachmentShareGrantRepository
                .findByTokenHash(resultIssueShareTokenService.hash(secondTokens.get(0))).orElseThrow();
        mockMvc.perform(post(
                        "/api/admin/result-issue-attachment-share-grants/{grantId}/revoke",
                        individuallyRevoked.getId()
                ).with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedBy").value(ADMIN_USERNAME));
        mockMvc.perform(get("/share/attachments/{token}", secondTokens.get(0)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/share/attachments/{token}", secondTokens.get(1)))
                .andExpect(status().isFound());

        ResultIssueAttachmentShareGrant unsafeGrant = resultIssueAttachmentShareGrantRepository
                .findByTokenHash(resultIssueShareTokenService.hash(secondTokens.get(1))).orElseThrow();
        ResultIssueAttachment unsafeAttachment = unsafeGrant.getAttachment();
        unsafeAttachment.setScanStatus(AttachmentScanStatus.INFECTED);
        resultIssueAttachmentRepository.saveAndFlush(unsafeAttachment);
        mockMvc.perform(get("/share/attachments/{token}", secondTokens.get(1)))
                .andExpect(status().isNotFound());

        ResultIssueAttachmentShareGrant expiredGrant = resultIssueAttachmentShareGrantRepository
                .findByTokenHash(resultIssueShareTokenService.hash(secondTokens.get(2))).orElseThrow();
        jdbcTemplate.update(
                "UPDATE result_issue_attachment_share_grants SET expires_at=now() - interval '1 second' WHERE id=?",
                expiredGrant.getId()
        );
        mockMvc.perform(get("/share/attachments/{token}", secondTokens.get(2)))
                .andExpect(status().isNotFound());
        assertThat(eventRepository.findById(kazan.event().getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(sportsRevisionBefore);
        assertThat(resultIssueRequestRepository.findById(linkedIssue.getId()).orElseThrow().getStatus())
                .isEqualTo(ResultIssueStatus.NEW);
    }

    @Test
    void journalXlsxExportHandlesEmptyNoAttachmentAndExplicitNoExpiryCases() throws Exception {
        AdminIssueFixture fixture = createGlobalIssueFixture(
                "No attachment export", "xlsx-empty", "Perm", "2026-09-01T06:00:00Z", 1
        );
        mockMvc.perform(post("/api/admin/result-issue-requests/export/xlsx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/result-issue-requests/export/xlsx")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noExpiry\":true,\"shareLifetimeDays\":30}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SHARE_LIFETIME"));
        mockMvc.perform(post("/api/admin/result-issue-requests/export/xlsx")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shareLifetimeDays\":0}"))
                .andExpect(status().isBadRequest());

        MvcResult empty = performXlsxExport(Map.of("location", "No such location"));
        assertThat(empty.getResponse().getHeader("X-Share-Batch-Id")).isNull();
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(empty.getResponse().getContentAsByteArray())
        )) {
            assertThat(workbook.getSheet("Обращения").getLastRowNum()).isZero();
            assertThat(workbook.getSheet("Вложения").getLastRowNum()).isZero();
            assertThat(findInformationValue(workbook.getSheet("Информация"), "Количество обращений"))
                    .isEqualTo("0");
        }
        MvcResult withoutAttachments = performXlsxExport(Map.of("eventId", fixture.event().getId()));
        assertThat(withoutAttachments.getResponse().getHeader("X-Share-Batch-Id")).isNull();
        assertThat(resultIssueShareBatchRepository.count()).isZero();

        ResultIssueAttachment attachment = createAdminAttachment(
                fixture.issues().getFirst(), "permanent-link.pdf",
                AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.CLEAN
        );
        attachmentObjectStorage.recordUploadedObject(
                attachment.getStorageKey(), attachment.getSizeBytes(), attachment.getDetectedContentType(), "etag"
        );
        MvcResult noExpiry = performXlsxExport(Map.of(
                "eventId", fixture.event().getId(),
                "noExpiry", true
        ));
        assertThat(noExpiry.getResponse().getHeader("X-Share-Batch-Id")).isNotBlank();
        assertThat(noExpiry.getResponse().getHeader("X-Share-Expires-At")).isNull();
        UUID batchId = UUID.fromString(noExpiry.getResponse().getHeader("X-Share-Batch-Id"));
        assertThat(resultIssueShareBatchRepository.findById(batchId).orElseThrow().getExpiresAt()).isNull();
    }

    @Test
    void journalXlsxExportUsesHistoricalSnapshotAndKeepsCurrentContextExplicit() throws Exception {
        AdminIssueFixture fixture = createGlobalIssueFixture(
                "Историческое событие", "xlsx-historical", "Kazan", "2026-05-10T06:00:00Z", 3
        );
        Race historicalRace = fixture.registrations().getFirst().getRace();
        Category historicalCategory = fixture.registrations().getFirst().getCategory();
        Registration registration = fixture.registrations().getFirst();
        ResultIssueRequest missingResultIssue = fixture.issues().getFirst();
        Race currentRace = new Race();
        currentRace.setEvent(fixture.event());
        currentRace.setSourceCode("current-race");
        currentRace.setName("Текущая Race");
        currentRace.setSlug("xlsx-historical-current-race");
        currentRace.setDisplayOrder(10);
        currentRace = raceRepository.saveAndFlush(currentRace);
        Category currentCategory = new Category();
        currentCategory.setRace(currentRace);
        currentCategory.setSourceName("CURRENT");
        currentCategory.setDisplayName("Текущая категория");
        currentCategory.setDisplayOrder(0);
        currentCategory = categoryRepository.saveAndFlush(currentCategory);
        UUID retirementOperationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO import_operations(
                    id, event_id, operation_mode, status, source_filename, file_sha256,
                    base_revision, plan_digest, created_by, preview_summary, created_at, updated_at
                ) VALUES (?, ?, 'EMERGENCY_REPLACE', 'PREVIEWED', 'xlsx-retire.csv', repeat('a', 64),
                          ?, repeat('b', 64), ?, '{}'::jsonb, now(), now())
                """, retirementOperationId, fixture.event().getId(),
                fixture.event().getResultDataRevision(), ADMIN_USERNAME);
        registration.setRace(currentRace);
        registration.setCategory(currentCategory);
        registration.setRetiredAt(Instant.now());
        registration.setRetiredByImportOperationId(retirementOperationId);
        registrationRepository.saveAndFlush(registration);
        jdbcTemplate.update(
                "UPDATE result_issue_requests SET snapshot_origin='LEGACY_BACKFILL_CURRENT_STATE' WHERE id=?",
                fixture.issues().get(1).getId()
        );
        ResultIssueAttachment attachment = createAdminAttachment(
                missingResultIssue, "historical.pdf", AttachmentUploadStatus.UPLOADED, AttachmentScanStatus.CLEAN
        );
        attachmentObjectStorage.recordUploadedObject(
                attachment.getStorageKey(), attachment.getSizeBytes(), attachment.getDetectedContentType(), "etag"
        );

        MvcResult historicalExport = performXlsxExport(Map.of(
                "raceId", historicalRace.getId(), "sort", "issueId", "direction", "asc"
        ));
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(historicalExport.getResponse().getContentAsByteArray())
        )) {
            Sheet issues = workbook.getSheet("Обращения");
            assertThat(issues.getLastRowNum()).isEqualTo(3);
            Row first = findRowByLongId(issues, missingResultIssue.getId());
            assertThat(cellText(first.getCell(5))).isEqualTo(historicalRace.getName());
            assertThat(cellText(first.getCell(9))).isEqualTo(historicalCategory.getDisplayName());
            assertThat(cellText(first.getCell(18))).isBlank();
            assertThat(cellText(first.getCell(19))).isBlank();
            assertThat(cellText(first.getCell(29))).isEqualTo("Текущая Race");
            assertThat(cellText(first.getCell(30))).isEqualTo("Текущая категория");
            assertThat(cellText(first.getCell(31))).isEqualTo("Да");
            Row legacy = findRowByLongId(issues, fixture.issues().get(1).getId());
            assertThat(cellText(legacy.getCell(28))).isEqualTo("LEGACY_BACKFILL_CURRENT_STATE");
        }
        MvcResult currentRaceExport = performXlsxExport(Map.of("raceId", currentRace.getId()));
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(currentRaceExport.getResponse().getContentAsByteArray())
        )) {
            assertThat(workbook.getSheet("Обращения").getLastRowNum()).isZero();
        }
    }

    @Test
    void journalXlsxStreamingExportHandlesMoreThanTenThousandIssues() throws Exception {
        AdminIssueFixture fixture = createGlobalIssueFixture(
                "Volume XLSX", "xlsx-volume", "Kazan", "2026-08-01T06:00:00Z", 1
        );
        insertSyntheticJournalRows(fixture, "X-", 100, 10_000);
        ResultIssueJournalExportRequest requestDto = objectMapper.readValue(
                objectMapper.writeValueAsBytes(Map.of("eventId", fixture.event().getId(), "sort", "issueId")),
                ResultIssueJournalExportRequest.class
        );
        Runtime runtime = Runtime.getRuntime();
        long heapBefore = runtime.totalMemory() - runtime.freeMemory();
        long started = System.nanoTime();
        ResultIssueJournalExportArtifact artifact = resultIssueJournalXlsxExportService.export(
                requestDto, ADMIN_USERNAME
        );
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        long heapAfter = runtime.totalMemory() - runtime.freeMemory();
        try {
            assertThat(artifact.issueCount()).isEqualTo(10_001);
            assertThat(artifact.attachmentCount()).isZero();
            assertThat(artifact.grantCount()).isZero();
            assertThat(artifact.shareBatchId()).isNull();
            assertThat(artifact.sizeBytes()).isPositive().isLessThan(25_000_000L);
            try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(artifact.path()))) {
                assertThat(workbook.getSheet("Обращения").getLastRowNum()).isEqualTo(10_001);
            }
            System.out.printf(
                    "STAGE_H_PERFORMANCE issues=%d attachments=%d elapsedMs=%d xlsxBytes=%d "
                            + "batchSize=%d heapBeforeBytes=%d heapAfterBytes=%d%n",
                    artifact.issueCount(), artifact.attachmentCount(), elapsedMillis,
                    artifact.sizeBytes(), ResultIssueJournalXlsxExportService.EXPORT_BATCH_SIZE,
                    heapBefore, heapAfter
            );
        } finally {
            artifact.delete();
        }
    }

    private MvcResult performXlsxExport(Map<String, ?> requestBody) throws Exception {
        MvcResult pending = mockMvc.perform(post("/api/admin/result-issue-requests/export/xlsx")
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(requestBody)))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(pending))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .andExpect(header().string(
                        "Content-Disposition", org.hamcrest.Matchers.containsString("attachment")
                ))
                .andExpect(header().string(
                        "Content-Disposition", org.hamcrest.Matchers.containsString("result-issue-journal-")
                ))
                .andReturn();
    }

    private static List<String> workbookShareUrls(byte[] xlsx) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            List<String> urls = new ArrayList<>();
            Sheet attachments = workbook.getSheet("Вложения");
            for (int rowIndex = 1; rowIndex <= attachments.getLastRowNum(); rowIndex++) {
                Cell link = attachments.getRow(rowIndex).getCell(11);
                if (link != null && link.getHyperlink() != null) {
                    urls.add(link.getHyperlink().getAddress());
                }
            }
            return List.copyOf(urls);
        }
    }

    private static List<String> tokens(List<String> shareUrls) {
        return shareUrls.stream()
                .map(url -> url.substring(url.lastIndexOf('/') + 1))
                .toList();
    }

    private static List<String> headerValues(Sheet sheet) {
        List<String> values = new ArrayList<>();
        for (Cell cell : sheet.getRow(0)) {
            values.add(cellText(cell));
        }
        return values;
    }

    private static Row findRowByLongId(Sheet sheet, long id) {
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && row.getCell(0) != null
                    && (long) row.getCell(0).getNumericCellValue() == id) {
                return row;
            }
        }
        throw new AssertionError("No XLSX row for id " + id);
    }

    private static String findInformationValue(Sheet sheet, String label) {
        for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row != null && label.equals(cellText(row.getCell(0)))) {
                return cellText(row.getCell(1));
            }
        }
        throw new AssertionError("No information row " + label);
    }

    private static String allWorkbookText(XSSFWorkbook workbook) {
        StringBuilder text = new StringBuilder();
        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    text.append(cellText(cell)).append('\n');
                    if (cell.getHyperlink() != null) {
                        text.append(cell.getHyperlink().getAddress()).append('\n');
                    }
                }
            }
        }
        return text.toString();
    }

    private static String cellText(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                yield value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
            }
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> cell.toString();
        };
    }

    private void assertImportPreviewStale(Long eventId, UUID operationId, byte[] content) {
        assertThatThrownBy(() -> importApplyService.apply(eventId, operationId, content, ADMIN_USERNAME))
                .isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_STALE");
    }

    private ResultIssueRequest resultIssue(
            Event event,
            Registration registration,
            Result result,
            ResultIssueType type,
            ResultCorrectionReason reason,
            String observedStatus
    ) {
        registration = registrationRepository.findById(registration.getId()).orElseThrow();
        ResultIssueRequest issue = new ResultIssueRequest();
        issue.setEvent(event);
        issue.setRegistration(registration);
        issue.setResult(result);
        issue.setIssueType(type);
        issue.setCorrectionReason(reason);
        issue.setStatus(ResultIssueStatus.NEW);
        issue.setContactEmail("stage-c@example.org");
        issue.setMessage("Stage C persistence check");
        if (type == ResultIssueType.RESULT_CORRECTION && result != null) {
            issue.setObservedGunTime(result.getGunTime());
            issue.setObservedChipTime(result.getChipTime());
        }
        issue.setObservedResultStatus(observedStatus);
        resultIssueSnapshotService.capture(issue);
        return resultIssueRequestRepository.saveAndFlush(issue);
    }

    private void assertMoveBlocked(
            Event event,
            List<Long> scopeRaceIds,
            String csv,
            String reasonCode
    ) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        long revision = eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision();
        ImportPreviewResponseDto preview = importPreviewService.preview(
                event.getId(), reasonCode + ".csv", bytes, ImportOperationMode.UPDATE_EXISTING,
                scopeRaceIds, 100, ADMIN_USERNAME
        );
        assertThat(preview.blockingErrorsPresent()).isTrue();
        assertThat(preview.rows().getFirst().reasonCode()).isEqualTo(reasonCode);
        assertThatThrownBy(() -> importApplyService.apply(
                event.getId(), preview.operationId(), bytes, ADMIN_USERNAME
        )).isInstanceOf(RequestConflictException.class)
                .extracting(exception -> ((RequestConflictException) exception).getCode())
                .isEqualTo("PREVIEW_BLOCKED");
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision())
                .isEqualTo(revision);
    }

    private StageF5Fixture stageF5Fixture(String slug) {
        EventSeries series = createSeries("Stage F.5 " + slug, slug + "-series");
        Event event = createEvent(
                series,
                "Past Stage F.5",
                slug,
                "Kazan",
                "2025-06-15T08:00:00Z"
        );
        Race race = new Race();
        race.setEvent(event);
        race.setSourceCode("5 km");
        race.setName("5 km");
        race.setSlug(slug + "-5-km");
        race.setPublicRankingBasis(RankingBasis.GUN_TIME);
        race.setDisplayOrder(0);
        race.setResultsPublicationStatus(ResultsPublicationStatus.DRAFT);
        race = raceRepository.saveAndFlush(race);

        Category childTwelve = savedCategory(race, "CHILD_12", "Child 12", null, null, null, 0);
        Category childSeventeen = savedCategory(race, "CHILD_17", "Child 17", null, null, null, 1);
        Category adultEighteen = savedCategory(
                race, "ADULT_18", "Adults 18-29", 18, 29, CategoryGender.MALE, 2
        );
        Category adultThirty = savedCategory(
                race, "ADULT_30", "Adults 30-39", 30, 39, CategoryGender.MALE, 3
        );
        rawAwardPolicyService.upsert(race.getId(), policy(AgeCalculationMode.EVENT_DATE), ADMIN_USERNAME);

        StartCluster clusterOne = new StartCluster();
        clusterOne.setRace(race);
        clusterOne.setCode("K1");
        clusterOne.setDisplayName("K1");
        clusterOne.setDisplayOrder(0);
        StartCluster clusterTwo = new StartCluster();
        clusterTwo.setRace(race);
        clusterTwo.setCode("K2");
        clusterTwo.setDisplayName("K2");
        clusterTwo.setDisplayOrder(1);
        List<StartCluster> clusters = startClusterRepository.saveAllAndFlush(List.of(clusterOne, clusterTwo));

        ImportBatch batch = new ImportBatch();
        batch.setEvent(event);
        batch.setRace(race);
        batch.setScopeType(ImportScopeType.RACE);
        batch.setSourceFilename(slug + ".csv");
        batch.setFileSha256("a".repeat(64));
        batch.setStatus(ImportBatchStatus.SUCCEEDED);
        batch.setTotalRows(5);
        batch.setImportedRows(5);
        batch.setFinishedAt(Instant.now());
        batch = importBatchRepository.saveAndFlush(batch);

        Registration minorTwelve = stageF5Registration(
                race, batch, childTwelve, clusters.get(0), 1, "A-12",
                LocalDate.of(2013, 1, 1), "CHILD_12"
        );
        Registration boundaryMinor = stageF5Registration(
                race, batch, childSeventeen, clusters.get(1), 2, "B-17",
                LocalDate.of(2007, 6, 16), "CHILD_17"
        );
        Registration exactlyEighteen = stageF5Registration(
                race, batch, adultEighteen, clusters.get(0), 3, "C-18",
                LocalDate.of(2007, 6, 15), "SOURCE_ADULT"
        );
        Registration adult = stageF5Registration(
                race, batch, adultThirty, clusters.get(1), 4, "D-35",
                LocalDate.of(1990, 1, 1), "SOURCE_ADULT"
        );
        UUID retirementOperationId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO import_operations(
                    id, event_id, operation_mode, status, source_filename, file_sha256,
                    base_revision, plan_digest, created_by, preview_summary, created_at, updated_at
                ) VALUES (?, ?, 'EMERGENCY_REPLACE', 'PREVIEWED', 'retired.csv', repeat('b', 64),
                          ?, repeat('c', 64), ?, '{}'::jsonb, now(), now())
                """, retirementOperationId, event.getId(),
                eventRepository.findById(event.getId()).orElseThrow().getResultDataRevision(), ADMIN_USERNAME);
        Registration retiredAdult = stageF5Registration(
                race, batch, adultThirty, clusters.get(1), 5, "OLD-35",
                LocalDate.of(1990, 1, 1), "SOURCE_ADULT"
        );
        retiredAdult.setRetiredAt(Instant.now());
        retiredAdult.setRetiredByImportOperationId(retirementOperationId);
        List<Registration> registrations = registrationRepository.saveAllAndFlush(List.of(
                minorTwelve, boundaryMinor, exactlyEighteen, adult, retiredAdult
        ));
        List<Result> results = new ArrayList<>();
        for (int index = 0; index < registrations.size(); index++) {
            Result result = new Result();
            result.setRegistration(registrations.get(index));
            result.setStatus("finished");
            result.setGunTime(Duration.ofSeconds(101 + index));
            result.setChipTime(Duration.ofSeconds(100 + index));
            results.add(result);
        }
        results = resultRepository.saveAllAndFlush(results);
        raceResultsPublicationService.publish(event.getId(), race.getId(), ADMIN_USERNAME);
        ResultIssueRequest issue = resultIssue(
                event,
                registrations.get(3),
                results.get(3),
                ResultIssueType.RESULT_CORRECTION,
                ResultCorrectionReason.OTHER,
                "finished"
        );
        return new StageF5Fixture(
                event,
                race,
                adultThirty,
                registrations.get(1).getId(),
                registrations.get(3).getId(),
                registrations.get(4).getId(),
                clusters.get(1).getId(),
                issue.getId()
        );
    }

    private Category savedCategory(
            Race race,
            String sourceName,
            String displayName,
            Integer minAge,
            Integer maxAge,
            CategoryGender gender,
            int displayOrder
    ) {
        Long categoryId = categoryAdminService.create(
                race.getId(),
                categoryRequest(sourceName, displayName, minAge, maxAge, gender, displayOrder, true),
                ADMIN_USERNAME
        ).id();
        return categoryRepository.findById(categoryId).orElseThrow();
    }

    private static UpsertCategoryRequest categoryRequest(
            String sourceName,
            String displayName,
            Integer minAge,
            Integer maxAge,
            CategoryGender gender,
            int displayOrder,
            boolean enabled
    ) {
        return new UpsertCategoryRequest(
                sourceName, displayName, minAge, maxAge, gender, displayOrder, enabled
        );
    }

    private static UpdateAwardPolicyRequest policy(AgeCalculationMode mode) {
        return new UpdateAwardPolicyRequest(
                RankingBasis.GUN_TIME,
                PrimaryStandingMode.ALL,
                3,
                true,
                mode,
                3,
                false
        );
    }

    private static Registration stageF5Registration(
            Race race,
            ImportBatch batch,
            Category category,
            StartCluster cluster,
            int row,
            String bib,
            LocalDate birthDate,
            String sourceCategory
    ) {
        Registration registration = new Registration();
        registration.setRace(race);
        registration.setImportBatch(batch);
        registration.setCategory(category);
        registration.setCluster(cluster);
        registration.setEntryKind(RegistrationEntryKind.PERSON);
        registration.setBib(bib);
        registration.setDisplayName("Runner " + bib);
        registration.setFirstName("Runner");
        registration.setLastName(bib);
        registration.setGender("male");
        registration.setBirthDate(birthDate);
        registration.setSourceCategory(sourceCategory);
        registration.setSourceRowNumber(row);
        registration.setSourceRowHash(Integer.toHexString(row).repeat(64).substring(0, 64));
        return registration;
    }

    private Event createPublishedEvent(String name, String slug) {
        EventSeries series = new EventSeries();
        series.setName(name);
        series.setSlug(slug + "-series");
        series.setActive(true);
        series = eventSeriesRepository.saveAndFlush(series);
        Event event = new Event();
        event.setEventSeries(series);
        event.setName(name);
        event.setSlug(slug);
        event.setStartsAt(Instant.parse("2027-06-15T06:00:00Z"));
        event.setPublicationStatus(EventPublicationStatus.PUBLISHED);
        event.setResultsPublicationStatus(ru.sportsresults.domain.ResultsPublicationStatus.PUBLISHED);
        return eventRepository.saveAndFlush(event);
    }

    private Event createOpenInquiryEvent(String name, String slug) {
        Event event = createPublishedEvent(name, slug);
        event.setStartsAt(Instant.now().minus(Duration.ofDays(2)));
        event.setEndsAt(Instant.now().minus(Duration.ofDays(1)));
        event = eventRepository.saveAndFlush(event);
        eventService.updateResultInquirySettings(event.getId(), new UpdateResultInquirySettingsRequest(
                true, 5, "timing@example.org"
        ), ADMIN_USERNAME);
        return event;
    }

    private EventSeries createSeries(String name, String slug) {
        EventSeries series = new EventSeries();
        series.setName(name);
        series.setSlug(slug);
        series.setActive(true);
        return eventSeriesRepository.saveAndFlush(series);
    }

    private Event createEvent(EventSeries series, String name, String slug, String location, String startsAt) {
        Event event = new Event();
        event.setEventSeries(series);
        event.setName(name);
        event.setSlug(slug);
        event.setLocation(location);
        event.setStartsAt(Instant.parse(startsAt));
        event.setPublicationStatus(EventPublicationStatus.PUBLISHED);
        event.setResultsPublicationStatus(ru.sportsresults.domain.ResultsPublicationStatus.PUBLISHED);
        return eventRepository.saveAndFlush(event);
    }

    private long createTemplateStart(Long seriesId, String name, Integer distanceMeters)
            throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", name);
        payload.put("distanceMeters", distanceMeters);
        payload.put("publicVisible", true);
        String body = objectMapper.writeValueAsString(payload);
        MvcResult result = mockMvc.perform(post("/api/admin/event-series/{seriesId}/start-templates", seriesId)
                        .with(httpBasic(ADMIN_USERNAME, ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asLong();
    }

    private Race createDraftRace(Event event, String sourceCode, int displayOrder) {
        RaceDto created = raceAdminService.create(
                event.getId(),
                new UpsertRaceRequest(sourceCode, "Старт " + sourceCode, null, null, null, displayOrder, true),
                ADMIN_USERNAME
        );
        return raceRepository.findById(created.id()).orElseThrow();
    }

    private static byte[] populateGeneratedTemplate(byte[] source, Long raceId) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(source));
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            Sheet target = null;
            for (Sheet sheet : workbook) {
                Row metadata = sheet.getRow(0);
                if (metadata == null || metadata.getCell(0) == null || metadata.getCell(1) == null) continue;
                if (TabularImportFileReader.SHEET_RACE_MARKER.equals(metadata.getCell(0).getStringCellValue())
                        && Math.round(metadata.getCell(1).getNumericCellValue()) == raceId) {
                    target = sheet;
                    break;
                }
            }
            if (target == null) throw new IllegalStateException("Generated template Race sheet not found");
            Row row = target.getRow(2);
            row.createCell(0).setCellValue("SYN-XLSX-1");
            row.createCell(1).setCellValue("Тестова");
            row.createCell(2).setCellValue("Анна");
            row.createCell(3).setCellValue("Ж");
            row.getCell(4).setCellValue(LocalDate.of(1992, 4, 3));
            row.getCell(5).setCellValue(Duration.ofHours(1).plusMinutes(2).plusSeconds(3).toMillis()
                    / 86_400_000d);
            row.getCell(6).setCellValue(Duration.ofHours(1).plusMinutes(1).plusSeconds(58).toMillis()
                    / 86_400_000d);
            row.createCell(7).setCellValue("Финишировал");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private ImportBatch raceImportBatch(Event event, Race race, String filename) {
        ImportBatch batch = new ImportBatch();
        batch.setEvent(event);
        batch.setRace(race);
        batch.setScopeType(ImportScopeType.RACE);
        batch.setSourceFilename(filename);
        batch.setFileSha256("a".repeat(64));
        batch.setStatus(ImportBatchStatus.SUCCEEDED);
        batch.setFinishedAt(Instant.now());
        return importBatchRepository.saveAndFlush(batch);
    }

    private Registration raceRegistration(Race race, ImportBatch batch, int row, String bib) {
        Registration registration = new Registration();
        registration.setRace(race);
        registration.setImportBatch(batch);
        registration.setEntryKind(RegistrationEntryKind.PERSON);
        registration.setBib(bib);
        registration.setDisplayName("Synthetic " + bib);
        registration.setSourceRowNumber(row);
        registration.setSourceRowHash("b".repeat(64));
        return registrationRepository.saveAndFlush(registration);
    }

    private long count(String table, String predicate) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + predicate,
                Long.class
        );
        return count == null ? 0 : count;
    }

    private Map<String, Object> resultIssueSnapshotState(Long issueId) {
        return jdbcTemplate.queryForMap("""
                SELECT observed_gun_time_ms, observed_chip_time_ms, observed_result_status,
                       snapshot_origin, snapshot_event_name, snapshot_event_location,
                       snapshot_event_starts_at, snapshot_sport_format_id,
                       snapshot_sport_format_name, snapshot_sport_format_code,
                       snapshot_race_id, snapshot_race_name, snapshot_race_code,
                       snapshot_race_distance_meters, snapshot_bib, snapshot_display_name,
                       snapshot_effective_category_name, snapshot_source_category,
                       snapshot_category_publicly_enabled, snapshot_ranking,
                       snapshot_import_batch_id, snapshot_source_row_number
                FROM result_issue_requests WHERE id=?
                """, issueId);
    }

    private static void assertAscendingNullsLast(List<Long> values) {
        boolean nullSeen = false;
        Long previous = null;
        for (Long value : values) {
            if (value == null) {
                nullSeen = true;
                continue;
            }
            assertThat(nullSeen).as("non-null value must not follow NULL").isFalse();
            if (previous != null) {
                assertThat(value).isGreaterThanOrEqualTo(previous);
            }
            previous = value;
        }
    }

    private String eventJson(String name, String slug) throws Exception {
        Long seriesId = eventSeriesRepository.findBySlug(slug + "-series")
                .map(EventSeries::getId)
                .orElseGet(() -> {
                    EventSeries series = new EventSeries();
                    series.setName(name);
                    series.setSlug(slug + "-series");
                    series.setActive(true);
                    return eventSeriesRepository.saveAndFlush(series).getId();
                });
        return objectMapper.writeValueAsString(java.util.Map.of(
                "eventSeriesId", seriesId,
                "name", name,
                "slug", slug,
                "startsAt", "2027-06-15T06:00:00Z",
                "timeZone", "Europe/Moscow",
                "publicationStatus", "PUBLISHED"
        ));
    }

    private String categoryJson(
            String sourceName,
            String displayName,
            Integer minAge,
            Integer maxAge,
            CategoryGender gender,
            int displayOrder,
            boolean enabled
    ) throws Exception {
        java.util.Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("sourceName", sourceName);
        value.put("displayName", displayName);
        value.put("minAge", minAge);
        value.put("maxAge", maxAge);
        value.put("gender", gender);
        value.put("displayOrder", displayOrder);
        value.put("enabled", enabled);
        return objectMapper.writeValueAsString(value);
    }

    private static String dobCategoryCsv() {
        return csvHeader() + """
                DOB,Runner,male,1987-06-20,5 km,1,30-39 Male,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String sourceCategoryFallbackCsv() {
        return csvHeader() + """
                Fallback,Runner,male,,5 km,1,30-39 Male,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Primary,Only,male,,5 km,2,,finished,2000.0,1900.0,2.0,2.0,,2.0,2.0,
                """;
    }

    private static String oneRowCsv(String gunTime) {
        return """
                name,surname,gender,birthdate,event,dorsal,category,status,times.official_:::finish:::,times.real_:::finish:::,rankings_:::full-1:::,rankings.gen_:::full-1:::,rankings.cat_:::full-1:::,netrankings_:::full-1:::,netrankings.gen_:::full-1:::,netrankings.cat_:::full-1:::
                Иван,Иванов,male,1990-01-01,5 km,A-1,Open,finished,%s,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """.formatted(gunTime);
    }

    private static String namesCsv() {
        return csvHeader() + """
                Alexey,,male,1990-01-01,5 km,123,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Александр,,male,1990-01-01,5 km,00123,Open,finished,2000.0,1900.0,2.0,2.0,2.0,2.0,2.0,2.0
                John,,male,1990-01-01,5 km,A123,Open,finished,3000.0,2900.0,3.0,3.0,3.0,3.0,3.0,3.0
                Евгений,,male,1990-01-01,5 km,456,Open,finished,4000.0,3900.0,4.0,4.0,4.0,4.0,4.0,4.0
                Ярослав,,male,1990-01-01,5 km,789,Open,finished,5000.0,4900.0,5.0,5.0,5.0,5.0,5.0,5.0
                """;
    }

    private static String awardsCsv() {
        return csvHeader() + """
                X,,male,,5 km,1,Other,finished,500.0,500.0,1.0,1.0,1.0,1.0,1.0,1.0
                A,,female,,5 km,2,Target,finished,1000.0,1000.0,2.0,1.0,1.0,2.0,1.0,1.0
                Y,,male,,5 km,3,Target,finished,1500.0,1500.0,3.0,2.0,2.0,3.0,2.0,2.0
                B,,female,,5 km,4,Target,finished,2000.0,2000.0,4.0,2.0,3.0,4.0,2.0,3.0
                C,,male,,5 km,5,Target,finished,3000.0,3000.0,5.0,3.0,4.0,5.0,3.0,4.0
                D,,female,,5 km,6,Target,finished,4000.0,4000.0,6.0,3.0,5.0,6.0,3.0,5.0
                """;
    }

    private static String fullStandingCsv(int participantCount) {
        StringBuilder csv = new StringBuilder(csvHeader());
        for (int place = 1; place <= participantCount; place++) {
            long gunTime = place * 1_000L;
            long chipTime = (participantCount - place + 1L) * 1_000L;
            csv.append(("Участник %02d,,male,,5 km,%d,18+ Male,finished," +
                    "%d.0,%d.0,%d.0,%d.0,%d.0,%d.0,%d.0,%d.0%n").formatted(
                    place, place, gunTime, chipTime,
                    place, place, place, participantCount - place + 1,
                    participantCount - place + 1, participantCount - place + 1
            ));
        }
        return csv.toString();
    }

    private static String tieStandingCsv() {
        return csvHeader() + """
                A,,male,1990-01-01,5 km,1,18+ Male,finished,1000.0,1000.0,1.0,1.0,1.0,1.0,1.0,1.0
                B,,male,1990-01-01,5 km,2,18+ Male,finished,1000.0,1000.0,1.0,1.0,1.0,1.0,1.0,1.0
                C,,male,1990-01-01,5 km,3,18+ Male,finished,2000.0,2000.0,3.0,3.0,3.0,3.0,3.0,3.0
                """;
    }

    private static String searchCsv() {
        return csvHeader() + """
                Мария,Петрова,female,,5 km,101,Women,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Марина,Иванова,female,,5 km,102,Women,finished,1100.0,1000.0,2.0,2.0,2.0,2.0,2.0,2.0
                Иван,Иванов,male,,5 km,103,Men,finished,1200.0,1100.0,3.0,1.0,1.0,3.0,1.0,1.0
                Иван,Сидоров,male,,10 km,201,Men,finished,2000.0,1900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Ивана,Иванова,female,,10 km,202,Women,finished,2100.0,2000.0,2.0,1.0,1.0,2.0,1.0,1.0
                """;
    }

    private static String categorySemanticsCsv() {
        return csvHeader() + """
                Base Male,,male,,5 km,1,18+ Male,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Base Female,,female,,5 km,2,18+ Female,finished,1100.0,1000.0,2.0,1.0,1.0,2.0,1.0,1.0
                Age Female,,female,,10 km,3,30–39 Female,finished,2000.0,1900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String statusesCsv() {
        return csvHeader() + """
                Видимый Финиш,,male,1990-01-01,5 km,1,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Видимый DQ,,female,1990-01-01,5 km,2,Open,disqualified,100.0,100.0,1.0,1.0,1.0,1.0,1.0,1.0
                Скрытый Старт,,male,1990-01-01,5 km,3,Open,notstarted,,,,,,,,
                Скрытый Running,,male,1990-01-01,5 km,4,Open,running,,,,,,,,
                Скрытый Quarantine,,female,1990-01-01,5 km,5,Open,quarantine,,,,,,,,
                Скрытый Unknown,,female,1990-01-01,5 km,6,Open,unknown,,,,,,,,
                """;
    }

    private static String inquiryCsv() {
        return csvHeader() + """
                Публичный,Результат,male,1990-01-01,5 km,817,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Скрытый,Статус,female,1991-02-03,10 km,0817,Internal Source,quarantine,2000.0,1900.0,,,,,,
                Нет,Результата,male,1992-03-04,20 km,8170,Internal Source,running,,,,,,,,
                """;
    }

    private static String adminIssueCsv(int count) {
        StringBuilder csv = new StringBuilder(csvHeader());
        for (int index = 1; index <= count; index++) {
            csv.append("Участник").append(index)
                    .append(",Очередь,male,1990-01-")
                    .append(String.format(java.util.Locale.ROOT, "%02d", index))
                    .append(",5 km,Q-").append(index)
                    .append(",Open,finished,")
                    .append(1_000 + index).append(".0,")
                    .append(900 + index).append(".0,")
                    .append(index).append(".0,")
                    .append(index).append(".0,")
                    .append(index).append(".0,")
                    .append(index).append(".0,")
                    .append(index).append(".0,")
                    .append(index).append(".0\n");
        }
        return csv.toString();
    }

    private static String inquiryVisibilityCsv() {
        return csvHeader() + """
                Видимый,Участник,male,1990-01-01,5 km,V-1,Internal Source,quarantine,1000.0,900.0,,,,,,
                """;
    }

    private static String duplicateInquiryBibCsv() {
        return csvHeader() + """
                Первый,Дубль,male,1990-01-10,5 km,1100,Open,notstarted,,,,,,,,
                Второй,Дубль,male,1991-02-20,5 km,1100,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String allPublicDuplicateBibCsv() {
        return csvHeader() + """
                Первый,Публичный,male,1990-01-10,5 km,4400,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Второй,Публичный,female,1991-02-20,5 km,4400,Open,finished,1100.0,1000.0,2.0,1.0,1.0,2.0,1.0,1.0
                """;
    }

    private static String allNonPublicDuplicateBibCsv() {
        return csvHeader() + """
                Первый,Непубличный,male,1990-01-10,5 km,5500,Open,notstarted,,,,,,,,
                Второй,Непубличный,female,1991-02-20,5 km,5500,Open,running,,,,,,,,
                """;
    }

    private static String threeCandidateBibCsv() {
        return csvHeader() + """
                Первый,Три,male,1990-01-10,5 km,6600,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Второй,Три,female,1991-02-20,5 km,6600,Open,notstarted,,,,,,,,
                Третий,Три,male,1992-03-30,5 km,6600,Open,running,,,,,,,,
                """;
    }

    private static String ambiguousThreeCandidateBibCsv() {
        return csvHeader() + """
                Первый,Три,male,1990-01-10,5 km,7700,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Второй,Три,female,1991-02-20,5 km,7700,Open,notstarted,,,,,,,,
                Третий,Три,male,1991-02-20,5 km,7700,Open,running,,,,,,,,
                """;
    }

    private static String sameDobInquiryBibCsv() {
        return csvHeader() + """
                Первый,Одинаковый,male,1990-01-10,5 km,2200,Open,notstarted,,,,,,,,
                Второй,Одинаковый,male,1990-01-10,5 km,2200,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String nullDobInquiryBibCsv() {
        return csvHeader() + """
                Без,Даты,male,,5 km,3300,Open,notstarted,,,,,,,,
                С,Датой,female,1992-04-12,5 km,3300,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String singleInquiryBibCsv(String firstName, String status) {
        return csvHeader() + firstName + ",Участник,male,1990-01-10,5 km,1100,Open,"
                + (status.equals("finished")
                ? "finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0"
                : "notstarted,,,,,,,,")
                + System.lineSeparator();
    }

    private static String rankingCsv() {
        return csvHeader() + """
                Ган Лидер,,male,1990-01-01,5 km,1,Open,finished,1000.0,2000.0,1.0,1.0,1.0,2.0,2.0,2.0
                Чип Лидер,,female,1990-01-01,5 km,2,Open,finished,2000.0,1000.0,2.0,1.0,1.0,1.0,1.0,1.0
                Другой Ган,,male,1990-01-01,10 km,3,Open,finished,3000.0,4000.0,1.0,1.0,1.0,2.0,2.0,2.0
                Другой Чип,,female,1990-01-01,10 km,4,Open,finished,4000.0,3000.0,2.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String noneStandingCsv() {
        return csvHeader() + """
                A,,male,1990-01-01,Массовый старт,A,Open,finished,1000.0,4000.0,1.0,1.0,1.0,4.0,2.0,4.0
                B,,female,1990-01-01,Массовый старт,B,Open,finished,2000.0,1000.0,2.0,1.0,1.0,1.0,1.0,1.0
                C,,female,1990-01-01,Массовый старт,C,Open,finished,3000.0,3000.0,3.0,2.0,2.0,3.0,2.0,3.0
                D,,male,1990-01-01,Массовый старт,D,Open,finished,4000.0,2000.0,4.0,2.0,2.0,2.0,1.0,2.0
                E,,female,1990-01-01,Массовый старт,E,Open,finished,5000.0,5000.0,5.0,3.0,3.0,5.0,3.0,5.0
                F,,male,1990-01-01,Массовый старт,F,Open,finished,6000.0,6000.0,6.0,3.0,3.0,6.0,3.0,6.0
                """;
    }

    private static String finishedFirstCsv() {
        return csvHeader() + """
                A,,male,1990-01-01,5 km,A,Open,disqualified,1063000.0,1063000.0,,,,,,
                B,,male,1990-01-01,5 km,B,Open,finished,2893000.0,2893000.0,1.0,1.0,1.0,1.0,1.0,1.0
                C,,male,1990-01-01,5 km,C,Open,finished,3240000.0,3240000.0,2.0,2.0,2.0,2.0,2.0,2.0
                """;
    }

    private static String finishedFirstMatrixCsv() {
        return csvHeader() + """
                Alpha,,male,,5 km,30,Open,finished,3000.0,,,,,,,
                Bravo,,male,,5 km,20,Open,finished,1000.0,2000.0,,,,,,
                Charlie,,male,,5 km,10,Open,finished,2000.0,3000.0,,,,,,
                Aaron,,male,,5 km,00,Open,disqualified,500.0,500.0,,,,,,
                Zulu,,male,,5 km,99,Open,disqualified,9000.0,9000.0,,,,,,
                NoTime,,male,,5 km,50,Open,disqualified,,,,,,,,
                """;
    }

    private static String bibSortingCsv() {
        List<String> bibs = java.util.Arrays.asList(
                "449", "45", "450", "9", "10", "100", "445", "446", "001", "01", "1", "2", "20",
                "A10", "A2", "VIP-1", null
        );
        StringBuilder csv = new StringBuilder(csvHeader());
        for (int index = 0; index < bibs.size(); index++) {
            int place = index + 1;
            csv.append("Runner ").append(place).append(",,male,,5 km,")
                    .append(bibs.get(index) == null ? "" : bibs.get(index))
                    .append(",Open,finished,")
                    .append(place * 1000L).append(".0,")
                    .append(place * 900L).append(".0,")
                    .append(place).append(".0,")
                    .append(place).append(".0,")
                    .append(place).append(".0,")
                    .append(place).append(".0,")
                    .append(place).append(".0,")
                    .append(place).append(".0\n");
        }
        return csv.toString();
    }

    private static String raceSeparationCsv() {
        return csvHeader() + """
                Участник,A,male,1990-01-01,Race A,A-1,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Участник,B,female,1991-01-01,Race B,B-1,Open,finished,2000.0,1900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Команда,C,,,Race C,C-1,Open,finished,3000.0,2900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String stageBAddCsv() {
        return csvHeader().stripTrailing() + ",clusterCode\n" + """
                Иван,Иванов,male,1990-01-01,5 km,A-1,Open,finished,500.0,450.0,1.0,1.0,1.0,1.0,1.0,1.0,
                Новый,Первый,male,,5 km,N-1,New Class,finished,1500.0,1400.0,2.0,2.0,1.0,2.0,2.0,1.0,A
                Новый,Без старта,female,,5 km,N-2,,notstarted,,,,,,,,,
                """;
    }

    private static String duplicateStageBAddCsv() {
        return csvHeader() + """
                Duplicate,One,male,,5 km,DUP-1,Open,finished,1200.0,1100.0,2.0,2.0,2.0,2.0,2.0,2.0
                Duplicate,Two,male,,5 km,DUP-1,Open,finished,1300.0,1200.0,3.0,3.0,3.0,3.0,3.0,3.0
                """;
    }

    private static String singleNewCsv(String bib, String gunTime) {
        return csvHeader() + "New,Runner,male,,5 km," + bib
                + ",Open,finished," + gunTime + ",1100.0,2.0,2.0,2.0,2.0,2.0,2.0\n";
    }

    private static String scopedStageBAddCsv() {
        return csvHeader() + """
                Ган Лидер,,male,1990-01-01,5 km,1,Open,finished,1000.0,2000.0,1.0,1.0,1.0,2.0,2.0,2.0
                Scope,Inside,male,,5 km,SCOPE-IN,Open,finished,1500.0,1400.0,2.0,2.0,2.0,2.0,2.0,2.0
                Scope,Outside,male,,10 km,SCOPE-OUT,Open,finished,3500.0,3400.0,2.0,2.0,2.0,2.0,2.0,2.0
                """;
    }

    private static String stageCInitialCsv() {
        return csvHeader() + """
                Альфа,Без изменений,male,1990-01-01,5 km,A-1,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Борис,Ручной,male,1990-01-01,5 km,B-1,Open,finished,2000.0,1900.0,2.0,2.0,2.0,2.0,2.0,2.0
                Вера,Старый,female,1990-01-01,5 km,C-1,Open,finished,3000.0,2900.0,3.0,1.0,1.0,3.0,1.0,1.0
                Глеб,Нет результата,male,1990-01-01,5 km,G-1,Open,notstarted,,,,,,,,
                Дарья,Обращение,female,1990-01-01,5 km,H-1,Open,disqualified,4000.0,3900.0,,,,,,
                Вне,Области,male,1990-01-01,10 km,O-1,Open,finished,5000.0,4900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String stageCUpdateCsv() {
        return csvHeader() + """
                Альфа,Без изменений,male,1990-01-01,5 km,A-1,Open,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Борис,Ручной,male,1990-01-01,5 km,B-1,Open,finished,2500.0,800.0,2.0,2.0,2.0,2.0,2.0,2.0
                Вера,Обновлённый,female,1990-01-01,5 km,C-1,Open,disqualified,,,,,,,,
                Глеб,Нет результата,male,1990-01-01,5 km,G-1,Open,finished,3500.0,3400.0,4.0,2.0,2.0,4.0,2.0,2.0
                Дарья,Обращение,male,1990-01-01,5 km,H-1,Open,finished,1500.0,1400.0,2.0,1.0,1.0,2.0,1.0,1.0
                Новый,Пропустить,male,1990-01-01,5 km,NEW-1,Open,finished,6000.0,5900.0,5.0,3.0,3.0,5.0,3.0,3.0
                Вне,Области,male,1990-01-01,10 km,O-1,Open,finished,5500.0,5400.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String stageCRaceMoveInitialCsv() {
        return csvHeader() + """
                Иван,Переезд,male,1990-01-01,5 km,MOVE-1,A30,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0
                Сидор,Цель,male,1990-01-01,10 km,TARGET-1,B30,finished,2000.0,1900.0,1.0,1.0,1.0,1.0,1.0,1.0
                """;
    }

    private static String stageCRaceMoveCsv(String birthDate, String clusterCode) {
        return csvHeader().stripTrailing() + ",clusterCode\n"
                + "Иван,Переезд,male," + birthDate
                + ",10 km,MOVE-1,B30,finished,1000.0,900.0,1.0,1.0,1.0,1.0,1.0,1.0,"
                + clusterCode + "\n";
    }

    private static String stageCRaceMoveWithoutDobCsv() {
        return "event,dorsal,status,clusterCode\n10 km,MOVE-1,finished,B\n";
    }

    private static String stageCRaceMoveWithoutClusterCsv() {
        return "event,dorsal,status,birthdate,category\n10 km,MOVE-1,finished,1990-01-01,B30\n";
    }

    private static String stageCAbsentFieldsCsv(String surname, boolean includeEmptyCluster) {
        return includeEmptyCluster
                ? "event,dorsal,status,surname,clusterCode\n5 km,A-1,finished," + surname + ",\n"
                : "event,dorsal,status,surname\n5 km,A-1,finished," + surname + "\n";
    }

    private static String stageCClearTimesCsv(String surname) {
        return csvHeader().stripTrailing() + ",clusterCode\n"
                + "Иван," + surname
                + ",male,1990-01-01,5 km,A-1,Open,finished,,,,,,,,,A\n";
    }

    private static String stageCCategoryCreationCsv(String surname) {
        return csvHeader().stripTrailing() + ",clusterCode\n"
                + "Иван," + surname
                + ",male,,5 km,A-1,New Source Category,finished,,,,,,,,,A\n";
    }

    private static String stageCRollbackCsv() {
        return csvHeader() + """
                Иван,После rollback,male,1990-01-01,5 km,A-1,Open,finished,9000.0,8900.0,9.0,9.0,9.0,9.0,9.0,9.0
                """;
    }

    private static String csvHeader() {
        return "name,surname,gender,birthdate,event,dorsal,category,status,times.official_:::finish:::,times.real_:::finish:::,rankings_:::full-1:::,rankings.gen_:::full-1:::,rankings.cat_:::full-1:::,netrankings_:::full-1:::,netrankings.gen_:::full-1:::,netrankings.cat_:::full-1:::\n";
    }

    private ImportReportDto importPublishedFixture(Long eventId, String filename, byte[] content) {
        ImportReportDto report = this.importService.importEvent(eventId, filename, content);
        if (report.status() == ImportBatchStatus.SUCCEEDED) {
            List<Race> races = raceRepository.findAllByEventIdOrderByDisplayOrderAsc(eventId);
            races.forEach(race -> race.setResultsPublicationStatus(
                    ru.sportsresults.domain.ResultsPublicationStatus.PUBLISHED
            ));
            raceRepository.saveAllAndFlush(races);
        }
        return report;
    }

    private static Path sample(String filename) {
        return Path.of("..", "samples", filename).toAbsolutePath().normalize();
    }

    private record CreatedIssueAccess(Long issueId, String token) {
    }

    private final class DraftAwareAwardPolicyService {
        private final AwardPolicyService delegate;

        private DraftAwareAwardPolicyService(AwardPolicyService delegate) {
            this.delegate = delegate;
        }

        private AwardPolicyDto get(Long raceId) {
            return delegate.get(raceId);
        }

        private AwardPolicyDto upsert(
                Long raceId,
                UpdateAwardPolicyRequest request,
                String actor
        ) {
            Race before = raceRepository.findById(raceId).orElseThrow();
            Long eventId = raceRepository.findEventIdByRaceId(raceId).orElseThrow();
            boolean wasPublished = before.getResultsPublicationStatus() == ResultsPublicationStatus.PUBLISHED;
            if (wasPublished) {
                raceResultsPublicationService.draft(
                        eventId, raceId, "Integration fixture policy change", actor
                );
            }
            try {
                AwardPolicyDto result = delegate.upsert(raceId, request, actor);
                Race after = raceRepository.findById(raceId).orElseThrow();
                if (after.isResultRecalculationRequired()) {
                    ResultRecalculationPreviewDto preview = resultRecalculationService.preview(
                            eventId, List.of(raceId), 0, actor
                    );
                    resultRecalculationService.apply(eventId, preview.operationId(), actor);
                }
                if (wasPublished) {
                    raceResultsPublicationService.publish(eventId, raceId, actor);
                }
                return result;
            } catch (RuntimeException exception) {
                Race afterFailure = raceRepository.findById(raceId).orElseThrow();
                if (wasPublished && !afterFailure.isResultRecalculationRequired()
                        && afterFailure.getResultsPublicationStatus() == ResultsPublicationStatus.DRAFT) {
                    raceResultsPublicationService.publish(eventId, raceId, actor);
                }
                throw exception;
            }
        }
    }

    private record StageF5Fixture(
            Event event,
            Race race,
            Category adultThirty,
            Long boundaryRegistrationId,
            Long adultRegistrationId,
            Long retiredRegistrationId,
            Long clusterTwoId,
            Long issueId
    ) {
    }

    private record AdminIssueFixture(
            Event event,
            List<Registration> registrations,
            List<ResultIssueRequest> issues
    ) {
    }
}
