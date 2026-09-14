package ru.sportsresults.service;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.ResultIssueJournalExportRequest;
import ru.sportsresults.config.ApplicationPublicUrlProperties;
import ru.sportsresults.repository.GlobalResultIssueExportProjection;
import ru.sportsresults.repository.ResultIssueAttachmentExportProjection;
import ru.sportsresults.repository.ResultIssueAttachmentRepository;
import ru.sportsresults.repository.ResultIssueJournalFilter;
import ru.sportsresults.repository.ResultIssueJournalQuery;
import ru.sportsresults.repository.ResultIssueRequestRepository;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

@Service
public class ResultIssueJournalXlsxExportService {

    public static final int EXPORT_BATCH_SIZE = 1_000;
    private static final int EXCEL_MAX_ROWS = 1_048_576;
    private static final int EXCEL_MAX_DATA_ROWS = EXCEL_MAX_ROWS - 1;
    private static final int MAX_CELL_TEXT = 32_767;
    private static final String TRUNCATION_MARKER = " … [TRUNCATED]";
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneOffset.UTC);
    private static final Logger LOGGER = LoggerFactory.getLogger(ResultIssueJournalXlsxExportService.class);

    private static final String[] ISSUE_HEADERS = {
            "ID обращения", "Мероприятие", "Город / место", "Дата мероприятия", "Формат",
            "Дистанция / Race", "Дистанция, м", "Bib", "Участник",
            "Категория на момент обращения", "Исходная категория", "Тип обращения", "Причина",
            "Статус обращения", "Архивировано", "Причина архивации", "Дата создания",
            "Дата обновления", "Статус результата на момент обращения", "Официальное время",
            "Чистое время", "Email для связи", "Сообщение", "Заявленное официальное время",
            "Заявленное чистое время", "Оценочное время старта", "Оценочное время финиша",
            "Количество вложений", "Snapshot origin", "Текущая Race", "Текущая категория",
            "Registration retired"
    };
    private static final String[] ATTACHMENT_HEADERS = {
            "ID обращения", "Мероприятие", "Race", "Bib", "Участник", "Attachment ID",
            "Имя файла", "Content-Type", "Размер, байт", "Upload status", "Scan status", "Ссылка"
    };

    private final ResultIssueRequestRepository issueRepository;
    private final ResultIssueAttachmentRepository attachmentRepository;
    private final GlobalResultIssueJournalService journalService;
    private final ResultIssueShareTokenService tokenService;
    private final ResultIssueSharePersistenceService sharePersistenceService;
    private final ApplicationPublicUrlProperties publicUrlProperties;

    public ResultIssueJournalXlsxExportService(
            ResultIssueRequestRepository issueRepository,
            ResultIssueAttachmentRepository attachmentRepository,
            GlobalResultIssueJournalService journalService,
            ResultIssueShareTokenService tokenService,
            ResultIssueSharePersistenceService sharePersistenceService,
            ApplicationPublicUrlProperties publicUrlProperties
    ) {
        this.issueRepository = issueRepository;
        this.attachmentRepository = attachmentRepository;
        this.journalService = journalService;
        this.tokenService = tokenService;
        this.sharePersistenceService = sharePersistenceService;
        this.publicUrlProperties = publicUrlProperties;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultIssueJournalExportArtifact export(ResultIssueJournalExportRequest request, String actor) {
        Instant startedAt = Instant.now();
        ResultIssueJournalQuery query = prepareQuery(request);
        ShareOptions shareOptions = shareOptions(request, startedAt);
        long issueCount = issueRepository.countJournal(query.filter());
        if (issueCount > EXCEL_MAX_DATA_ROWS) {
            throw new InvalidRequestException(
                    "XLSX_ISSUE_ROW_LIMIT_EXCEEDED",
                    "Export contains more issues than one XLSX sheet can hold"
            );
        }

        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("result-issue-journal-", ".xlsx");
            UUID candidateBatchId = UUID.randomUUID();
            WorkbookResult workbookResult = writeWorkbook(
                    temporaryFile, query, issueCount, candidateBatchId, shareOptions, startedAt
            );
            UUID persistedBatchId = null;
            Instant persistedExpiry = null;
            if (!workbookResult.grants().isEmpty()) {
                sharePersistenceService.persist(
                        candidateBatchId,
                        normalizeActor(actor),
                        startedAt,
                        shareOptions.expiresAt(),
                        issueCount,
                        workbookResult.grants()
                );
                persistedBatchId = candidateBatchId;
                persistedExpiry = shareOptions.expiresAt();
            }
            long sizeBytes = Files.size(temporaryFile);
            long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
            LOGGER.info(
                    "Generated Result Issue journal XLSX: issues={}, attachments={}, grants={}, bytes={}, elapsedMs={}",
                    issueCount, workbookResult.attachmentCount(), workbookResult.grants().size(),
                    sizeBytes, elapsedMillis
            );
            return new ResultIssueJournalExportArtifact(
                    temporaryFile,
                    "result-issue-journal-" + FILE_TIMESTAMP.format(startedAt) + ".xlsx",
                    sizeBytes,
                    issueCount,
                    workbookResult.attachmentCount(),
                    workbookResult.grants().size(),
                    persistedBatchId,
                    persistedExpiry
            );
        } catch (IOException exception) {
            deleteQuietly(temporaryFile);
            throw new IllegalStateException("Could not generate Result Issue journal XLSX", exception);
        } catch (RuntimeException exception) {
            deleteQuietly(temporaryFile);
            throw exception;
        }
    }

    private WorkbookResult writeWorkbook(
            Path target,
            ResultIssueJournalQuery query,
            long issueCount,
            UUID candidateBatchId,
            ShareOptions shareOptions,
            Instant generatedAt
    ) throws IOException {
        List<ResultIssueShareGrantDraft> grants = new ArrayList<>();
        Set<Long> grantedAttachmentIds = new HashSet<>();
        long attachmentCount = 0;
        String publicBaseUrl = null;

        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        workbook.setCompressTempFiles(true);
        try {
            WorkbookStyles styles = new WorkbookStyles(workbook);
            Sheet issuesSheet = workbook.createSheet("Обращения");
            Sheet attachmentsSheet = workbook.createSheet("Вложения");
            Sheet informationSheet = workbook.createSheet("Информация");
            configureSheet(issuesSheet, ISSUE_HEADERS, issueWidths());
            configureSheet(attachmentsSheet, ATTACHMENT_HEADERS, attachmentWidths());
            writeHeader(issuesSheet, ISSUE_HEADERS, styles.header());
            writeHeader(attachmentsSheet, ATTACHMENT_HEADERS, styles.header());

            int issueRowIndex = 1;
            int attachmentRowIndex = 1;
            int offset = 0;
            while (offset < issueCount) {
                List<GlobalResultIssueExportProjection> issues = issueRepository.findJournalExportBatch(
                        query.filter(), offset, EXPORT_BATCH_SIZE, query.sort(), query.direction()
                );
                if (issues.isEmpty()) {
                    break;
                }
                List<Long> issueIds = issues.stream().map(GlobalResultIssueExportProjection::issueId).toList();
                List<ResultIssueAttachmentExportProjection> attachments =
                        attachmentRepository.findExportMetadataByIssueIds(issueIds);
                Map<Long, List<ResultIssueAttachmentExportProjection>> attachmentsByIssue = new HashMap<>();
                for (ResultIssueAttachmentExportProjection attachment : attachments) {
                    attachmentsByIssue.computeIfAbsent(attachment.issueId(), ignored -> new ArrayList<>())
                            .add(attachment);
                }

                for (GlobalResultIssueExportProjection issue : issues) {
                    List<ResultIssueAttachmentExportProjection> issueAttachments =
                            attachmentsByIssue.getOrDefault(issue.issueId(), List.of());
                    writeIssueRow(issuesSheet.createRow(issueRowIndex++), issue, issueAttachments.size(), styles);
                    for (ResultIssueAttachmentExportProjection attachment : issueAttachments) {
                        if (attachmentRowIndex >= EXCEL_MAX_ROWS) {
                            throw new InvalidRequestException(
                                    "XLSX_ATTACHMENT_ROW_LIMIT_EXCEEDED",
                                    "Export contains more attachments than one XLSX sheet can hold"
                            );
                        }
                        String shareUrl = null;
                        if (attachment.shareable() && grantedAttachmentIds.add(attachment.attachmentId())) {
                            if (publicBaseUrl == null) {
                                publicBaseUrl = requirePublicBaseUrl();
                            }
                            GeneratedShareToken token = tokenService.generate();
                            shareUrl = publicBaseUrl + "/share/attachments/" + token.rawToken();
                            grants.add(new ResultIssueShareGrantDraft(attachment.attachmentId(), token.tokenHash()));
                        }
                        writeAttachmentRow(
                                attachmentsSheet.createRow(attachmentRowIndex++),
                                issue,
                                attachment,
                                shareUrl,
                                styles,
                                workbook.getCreationHelper()
                        );
                        attachmentCount++;
                    }
                }
                offset = Math.addExact(offset, issues.size());
                if (issues.size() < EXPORT_BATCH_SIZE) {
                    break;
                }
            }

            issuesSheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, issueRowIndex - 1), 0, ISSUE_HEADERS.length - 1));
            attachmentsSheet.setAutoFilter(new CellRangeAddress(
                    0, Math.max(0, attachmentRowIndex - 1), 0, ATTACHMENT_HEADERS.length - 1
            ));
            writeInformationSheet(
                    informationSheet,
                    generatedAt,
                    grants.isEmpty() ? null : candidateBatchId,
                    grants.isEmpty() ? null : shareOptions.expiresAt(),
                    query,
                    issueCount,
                    attachmentCount,
                    grants.size(),
                    styles
            );
            try (OutputStream output = Files.newOutputStream(target)) {
                workbook.write(output);
            }
            return new WorkbookResult(attachmentCount, List.copyOf(grants));
        } finally {
            try {
                workbook.close();
            } finally {
                workbook.dispose();
            }
        }
    }

    private ResultIssueJournalQuery prepareQuery(ResultIssueJournalExportRequest request) {
        return journalService.prepareQuery(
                request.issueId(), request.eventId(), request.location(), request.eventDateFrom(), request.eventDateTo(),
                request.createdFrom(), request.createdTo(), request.sportFormatId(), request.sportFormatCode(),
                request.raceId(), request.raceCode(), request.bib(), request.participant(), request.issueType(),
                request.correctionReason(), request.statuses(), request.queueScope(), request.queueArchiveReason(),
                request.queueArchivedImportOperationId(),
                request.sort() == null || request.sort().isBlank() ? "createdAt" : request.sort(),
                request.direction() == null || request.direction().isBlank() ? "desc" : request.direction()
        );
    }

    private static ShareOptions shareOptions(ResultIssueJournalExportRequest request, Instant now) {
        boolean noExpiry = Boolean.TRUE.equals(request.noExpiry());
        if (noExpiry && request.shareLifetimeDays() != null) {
            throw new InvalidRequestException(
                    "INVALID_SHARE_LIFETIME", "shareLifetimeDays must be omitted when noExpiry is true"
            );
        }
        int lifetimeDays = request.shareLifetimeDays() == null ? 30 : request.shareLifetimeDays();
        if (!noExpiry && (lifetimeDays < 1 || lifetimeDays > 365)) {
            throw new InvalidRequestException(
                    "INVALID_SHARE_LIFETIME", "shareLifetimeDays must be between 1 and 365"
            );
        }
        return new ShareOptions(noExpiry ? null : now.plus(lifetimeDays, ChronoUnit.DAYS));
    }

    private String requirePublicBaseUrl() {
        String value = publicUrlProperties.publicBaseUrl();
        if (value == null || value.isBlank()) {
            throw new ResultIssueShareStorageUnavailableException(
                    new IllegalStateException("app.public-base-url is not configured")
            );
        }
        String normalized = value.strip();
        try {
            URI uri = new URI(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new URISyntaxException(normalized, "Unsupported public base URL");
            }
        } catch (URISyntaxException exception) {
            throw new ResultIssueShareStorageUnavailableException(exception);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static void writeIssueRow(
            Row row,
            GlobalResultIssueExportProjection issue,
            int attachmentCount,
            WorkbookStyles styles
    ) {
        int column = 0;
        number(row, column++, issue.issueId());
        text(row, column++, issue.eventName());
        text(row, column++, issue.eventLocation());
        instant(row, column++, issue.eventStartsAt(), styles.dateTime());
        text(row, column++, issue.sportFormatName());
        text(row, column++, issue.raceName());
        decimal(row, column++, issue.raceDistanceMeters());
        text(row, column++, issue.bib());
        text(row, column++, issue.displayName());
        text(row, column++, issue.categoryName());
        text(row, column++, issue.sourceCategory());
        text(row, column++, name(issue.issueType()));
        text(row, column++, name(issue.correctionReason()));
        text(row, column++, name(issue.status()));
        instant(row, column++, issue.queueArchivedAt(), styles.dateTime());
        text(row, column++, name(issue.queueArchiveReason()));
        instant(row, column++, issue.createdAt(), styles.dateTime());
        instant(row, column++, issue.updatedAt(), styles.dateTime());
        text(row, column++, issue.observedResultStatus());
        duration(row, column++, issue.observedGunTime(), styles.duration());
        duration(row, column++, issue.observedChipTime(), styles.duration());
        text(row, column++, issue.contactEmail());
        text(row, column++, issue.message(), styles.wrappedText());
        duration(row, column++, issue.claimedGunTime(), styles.duration());
        duration(row, column++, issue.claimedChipTime(), styles.duration());
        instant(row, column++, issue.estimatedStartAt(), styles.dateTime());
        instant(row, column++, issue.estimatedFinishAt(), styles.dateTime());
        number(row, column++, attachmentCount);
        text(row, column++, name(issue.snapshotOrigin()));
        text(row, column++, issue.currentRaceName());
        text(row, column++, issue.currentCategoryName());
        text(row, column, issue.registrationRetiredAt() == null ? "Нет" : "Да");
    }

    private static void writeAttachmentRow(
            Row row,
            GlobalResultIssueExportProjection issue,
            ResultIssueAttachmentExportProjection attachment,
            String shareUrl,
            WorkbookStyles styles,
            CreationHelper creationHelper
    ) {
        int column = 0;
        number(row, column++, issue.issueId());
        text(row, column++, issue.eventName());
        text(row, column++, issue.raceName());
        text(row, column++, issue.bib());
        text(row, column++, issue.displayName());
        number(row, column++, attachment.attachmentId());
        text(row, column++, attachment.originalFileName());
        text(row, column++, attachment.detectedContentType() == null
                ? attachment.contentType() : attachment.detectedContentType());
        number(row, column++, attachment.sizeBytes());
        text(row, column++, name(attachment.uploadStatus()));
        text(row, column++, name(attachment.scanStatus()));
        Cell linkCell = row.createCell(column);
        if (shareUrl == null) {
            linkCell.setCellValue(safeText("Недоступно: "
                    + name(attachment.uploadStatus()) + " / " + name(attachment.scanStatus())));
        } else {
            linkCell.setCellValue("Открыть файл");
            var hyperlink = creationHelper.createHyperlink(HyperlinkType.URL);
            hyperlink.setAddress(shareUrl);
            linkCell.setHyperlink(hyperlink);
            linkCell.setCellStyle(styles.hyperlink());
        }
    }

    private static void writeInformationSheet(
            Sheet sheet,
            Instant generatedAt,
            UUID batchId,
            Instant expiresAt,
            ResultIssueJournalQuery query,
            long issueCount,
            long attachmentCount,
            long grantCount,
            WorkbookStyles styles
    ) {
        sheet.setDisplayGridlines(false);
        sheet.setColumnWidth(0, 34 * 256);
        sheet.setColumnWidth(1, 100 * 256);
        Row header = sheet.createRow(0);
        text(header, 0, "Параметр", styles.header());
        text(header, 1, "Значение", styles.header());
        int row = 1;
        row = information(sheet, row, "Дата формирования", generatedAt, styles.dateTime());
        row = information(sheet, row, "Share Batch ID", batchId == null ? null : batchId.toString(), null);
        row = information(
                sheet, row, "Срок действия ссылок",
                batchId == null ? "Ссылки не создавались" : expiresAt == null ? "Без срока" : expiresAt,
                expiresAt == null ? null : styles.dateTime()
        );
        row = information(sheet, row, "Фильтры export", describeFilter(query.filter()), styles.wrappedText());
        row = information(sheet, row, "Сортировка", query.sort().apiName() + " "
                + query.direction().name().toLowerCase(), null);
        row = information(sheet, row, "Количество обращений", issueCount, null);
        row = information(sheet, row, "Количество вложений", attachmentCount, null);
        information(sheet, row, "Количество созданных share links", grantCount, null);
        sheet.createFreezePane(0, 1);
    }

    private static int information(
            Sheet sheet,
            int rowIndex,
            String label,
            Object value,
            CellStyle valueStyle
    ) {
        Row row = sheet.createRow(rowIndex);
        text(row, 0, label);
        if (value instanceof Instant instant) {
            instant(row, 1, instant, valueStyle);
        } else if (value instanceof Number number) {
            row.createCell(1).setCellValue(number.doubleValue());
        } else {
            text(row, 1, value == null ? null : value.toString(), valueStyle);
        }
        return rowIndex + 1;
    }

    private static String describeFilter(ResultIssueJournalFilter filter) {
        StringJoiner values = new StringJoiner("; ");
        add(values, "issueId", filter.issueId());
        add(values, "eventId", filter.eventId());
        add(values, "location", filter.location());
        add(values, "eventStartsAtFrom", filter.eventStartsAtFrom());
        add(values, "eventStartsAtToExclusive", filter.eventStartsAtToExclusive());
        add(values, "createdFrom", filter.createdFrom());
        add(values, "createdTo", filter.createdTo());
        add(values, "sportFormatId", filter.sportFormatId());
        add(values, "sportFormatCode", filter.sportFormatCode());
        add(values, "raceId", filter.raceId());
        add(values, "raceCode", filter.raceCode());
        add(values, "bib", filter.bib());
        add(values, "participant", filter.participant());
        add(values, "issueType", filter.issueType());
        add(values, "correctionReason", filter.correctionReason());
        add(values, "statuses", filter.statuses().isEmpty() ? null : filter.statuses());
        add(values, "queueScope", filter.queueScope());
        add(values, "queueArchiveReason", filter.queueArchiveReason());
        add(values, "queueArchivedImportOperationId", filter.queueArchivedImportOperationId());
        return values.length() == 0 ? "Без фильтров" : values.toString();
    }

    private static void add(StringJoiner values, String name, Object value) {
        if (value != null) {
            values.add(name + "=" + value);
        }
    }

    private static void configureSheet(Sheet sheet, String[] headers, int[] widths) {
        sheet.setDisplayGridlines(false);
        sheet.createFreezePane(0, 1);
        for (int index = 0; index < headers.length; index++) {
            sheet.setColumnWidth(index, widths[index] * 256);
        }
    }

    private static void writeHeader(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(30);
        for (int index = 0; index < headers.length; index++) {
            text(row, index, headers[index], style);
        }
    }

    private static int[] issueWidths() {
        return new int[]{
                14, 28, 20, 20, 20, 22, 14, 12, 28, 25, 22, 20, 20, 20, 20, 22,
                20, 20, 24, 18, 18, 30, 48, 22, 22, 20, 20, 18, 28, 22, 24, 20
        };
    }

    private static int[] attachmentWidths() {
        return new int[]{14, 28, 22, 12, 28, 16, 32, 24, 16, 20, 18, 26};
    }

    private static void text(Row row, int column, String value) {
        text(row, column, value, null);
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(safeText(value));
        }
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void number(Row row, int column, long value) {
        row.createCell(column).setCellValue(value);
    }

    private static void number(Row row, int column, Long value) {
        if (value != null) {
            row.createCell(column).setCellValue(value);
        } else {
            row.createCell(column);
        }
    }

    private static void decimal(Row row, int column, BigDecimal value) {
        if (value != null) {
            row.createCell(column).setCellValue(value.doubleValue());
        } else {
            row.createCell(column);
        }
    }

    private static void instant(Row row, int column, Instant value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(Date.from(value));
            cell.setCellStyle(style);
        }
    }

    private static void duration(Row row, int column, Duration value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.toMillis() / 86_400_000d);
            cell.setCellStyle(style);
        }
    }

    static String safeText(String value) {
        boolean dangerous = !value.isEmpty() && switch (value.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r' -> true;
            default -> false;
        };
        int prefixLength = dangerous ? 1 : 0;
        int maxSourceLength = MAX_CELL_TEXT - prefixLength;
        String bounded = value;
        if (bounded.length() > maxSourceLength) {
            int retained = maxSourceLength - TRUNCATION_MARKER.length();
            bounded = bounded.substring(0, retained) + TRUNCATION_MARKER;
        }
        return dangerous ? "'" + bounded : bounded;
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRequestException("INVALID_ACTOR", "Authenticated actor is required");
        }
        String normalized = actor.strip();
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // The exact temporary export path is intentionally best-effort cleanup only.
        }
    }

    private record ShareOptions(Instant expiresAt) {
    }

    private record WorkbookResult(long attachmentCount, List<ResultIssueShareGrantDraft> grants) {
    }

    private record WorkbookStyles(
            CellStyle header,
            CellStyle dateTime,
            CellStyle duration,
            CellStyle wrappedText,
            CellStyle hyperlink
    ) {
        WorkbookStyles(SXSSFWorkbook workbook) {
            this(
                    header(workbook),
                    dateTime(workbook),
                    duration(workbook),
                    wrappedText(workbook),
                    hyperlink(workbook)
            );
        }

        private static CellStyle header(SXSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            style.setFont(font);
            style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            style.setWrapText(true);
            style.setBorderBottom(BorderStyle.THIN);
            return style;
        }

        private static CellStyle dateTime(SXSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss"));
            return style;
        }

        private static CellStyle duration(SXSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat("[h]:mm:ss.000"));
            return style;
        }

        private static CellStyle wrappedText(SXSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setWrapText(true);
            style.setVerticalAlignment(VerticalAlignment.TOP);
            return style;
        }

        private static CellStyle hyperlink(SXSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setColor(IndexedColors.BLUE.getIndex());
            font.setUnderline(Font.U_SINGLE);
            style.setFont(font);
            return style;
        }
    }
}
