package ru.sportsresults.service;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.Race;
import ru.sportsresults.importing.TabularImportFileReader;
import ru.sportsresults.repository.EventRepository;
import ru.sportsresults.repository.RaceRepository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class TimingXlsxTemplateService {

    public static final int TEMPLATE_FORMAT_VERSION = 1;
    private static final int MAX_TEMPLATE_ROWS = 200_000;
    private static final List<String> HEADERS = List.of(
            "Стартовый номер", "Фамилия", "Имя", "Пол", "Дата рождения",
            "Официальное время", "Чистое время", "Статус", "Категория", "Кластер"
    );
    private static final List<String> STATUSES = List.of(
            "Финишировал", "Не стартовал", "Дисквалифицирован", "Карантин", "На дистанции"
    );

    private final EventRepository eventRepository;
    private final RaceRepository raceRepository;

    public TimingXlsxTemplateService(EventRepository eventRepository, RaceRepository raceRepository) {
        this.eventRepository = eventRepository;
        this.raceRepository = raceRepository;
    }

    @Transactional(readOnly = true)
    public GeneratedImportTemplate generate(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_NOT_FOUND", "Event not found"));
        List<Race> races = raceRepository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId);
        if (races.isEmpty()) {
            throw new InvalidRequestException(
                    "EVENT_HAS_NO_RACES", "Add at least one Start before downloading a result template"
            );
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = styles(workbook);
            instruction(workbook, event, styles);
            Set<String> names = new HashSet<>();
            for (Race race : races) resultSheet(workbook, race, uniqueSheetName(race.getName(), names), styles);
            metadata(workbook, event, races);
            workbook.setActiveSheet(0);
            workbook.write(output);
            return new GeneratedImportTemplate(filename(event.getName()), output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("XLSX template could not be generated", exception);
        }
    }

    private static void instruction(XSSFWorkbook workbook, Event event, Styles styles) {
        Sheet sheet = workbook.createSheet("Инструкция");
        sheet.setColumnWidth(0, 32 * 256);
        sheet.setColumnWidth(1, 95 * 256);
        row(sheet, 0, "Шаблон результатов", event.getName(), styles.title());
        row(sheet, 2, "Как заполнить", "Один лист — один Старт. Одна строка — один участник.", styles.text());
        row(sheet, 3, "Обязательно", "Стартовый номер и Статус. Для участника желательно указать имя и фамилию.", styles.text());
        row(sheet, 4, "Необязательно", "Пол, дата рождения, оба времени, категория и кластер.", styles.text());
        row(sheet, 5, "Дата рождения", "ДД.ММ.ГГГГ. Можно вставить настоящее значение даты Excel.", styles.text());
        row(sheet, 6, "Время", "Длительность в формате [ч]:мм:сс или [ч]:мм:сс.000; часы могут быть больше 24.", styles.text());
        row(sheet, 7, "Статусы", String.join(", ", STATUSES) + ".", styles.text());
        row(sheet, 8, "Важно", "Не переименовывайте заголовки и не редактируйте скрытые служебные данные.", styles.text());
        row(sheet, 9, "Загрузка", "После заполнения загрузите этот .xlsx обратно в раздел «Загрузка результатов».", styles.text());
    }

    private static void row(Sheet sheet, int index, String label, String value, CellStyle style) {
        Row row = sheet.createRow(index);
        row.createCell(0).setCellValue(label);
        row.getCell(0).setCellStyle(style);
        row.createCell(1).setCellValue(value);
        row.getCell(1).setCellStyle(style);
    }

    private static void resultSheet(XSSFWorkbook workbook, Race race, String name, Styles styles) {
        Sheet sheet = workbook.createSheet(name);
        Row metadata = sheet.createRow(0);
        metadata.createCell(0).setCellValue(TabularImportFileReader.SHEET_RACE_MARKER);
        metadata.createCell(1).setCellValue(race.getId());
        metadata.setZeroHeight(true);

        Row header = sheet.createRow(1);
        CreationHelper helper = workbook.getCreationHelper();
        for (int index = 0; index < HEADERS.size(); index++) {
            var cell = header.createCell(index);
            cell.setCellValue(HEADERS.get(index));
            cell.setCellStyle(index == 0 || index == 7 ? styles.requiredHeader() : styles.optionalHeader());
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(index);
            anchor.setCol2(index + 2);
            anchor.setRow1(1);
            anchor.setRow2(4);
            Comment comment = sheet.createDrawingPatriarch().createCellComment(anchor);
            comment.setAuthor("Sports Results");
            comment.setString(helper.createRichTextString(index == 0 || index == 7
                    ? "Обязательное поле"
                    : "Необязательное поле"));
            cell.setCellComment(comment);
        }

        sheet.setDefaultColumnStyle(4, styles.date());
        sheet.setDefaultColumnStyle(5, styles.duration());
        sheet.setDefaultColumnStyle(6, styles.duration());
        Row firstDataRow = sheet.createRow(2);
        firstDataRow.createCell(4).setCellStyle(styles.date());
        firstDataRow.createCell(5).setCellStyle(styles.duration());
        firstDataRow.createCell(6).setCellStyle(styles.duration());
        validation(sheet, 3, new String[]{"М", "Ж"});
        validation(sheet, 7, STATUSES.toArray(String[]::new));
        sheet.createFreezePane(0, 2);
        sheet.setAutoFilter(new CellRangeAddress(1, MAX_TEMPLATE_ROWS + 1, 0, HEADERS.size() - 1));
        int[] widths = {18, 22, 20, 11, 17, 22, 19, 23, 20, 18};
        for (int index = 0; index < widths.length; index++) sheet.setColumnWidth(index, widths[index] * 256);
    }

    private static void validation(Sheet sheet, int column, String[] values) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
        DataValidation validation = helper.createValidation(
                constraint, new CellRangeAddressList(2, MAX_TEMPLATE_ROWS + 1, column, column)
        );
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private static void metadata(XSSFWorkbook workbook, Event event, List<Race> races) {
        Sheet sheet = workbook.createSheet(TabularImportFileReader.META_SHEET);
        meta(sheet, 0, "templateFormatVersion", Integer.toString(TEMPLATE_FORMAT_VERSION));
        meta(sheet, 1, "eventId", event.getId().toString());
        meta(sheet, 2, "generatedAt", Instant.now().toString());
        meta(sheet, 3, "raceIds", races.stream().map(race -> race.getId().toString()).sorted().collect(java.util.stream.Collectors.joining(",")));
        workbook.setSheetVisibility(workbook.getSheetIndex(sheet), SheetVisibility.VERY_HIDDEN);
    }

    private static void meta(Sheet sheet, int rowIndex, String key, String value) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(key);
        row.createCell(1).setCellValue(value);
    }

    private static Styles styles(XSSFWorkbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle title = workbook.createCellStyle();
        title.setFont(bold);
        CellStyle text = workbook.createCellStyle();
        text.setWrapText(true);
        CellStyle required = workbook.createCellStyle();
        required.setFont(bold);
        required.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        required.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle optional = workbook.createCellStyle();
        optional.setFont(bold);
        optional.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        optional.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        CellStyle date = workbook.createCellStyle();
        date.setDataFormat(workbook.createDataFormat().getFormat("dd.mm.yyyy"));
        CellStyle duration = workbook.createCellStyle();
        duration.setDataFormat(workbook.createDataFormat().getFormat("[h]:mm:ss.000"));
        return new Styles(title, text, required, optional, date, duration);
    }

    private static String uniqueSheetName(String requested, Set<String> used) {
        String base = WorkbookUtil.createSafeSheetName(requested == null ? "Старт" : requested).strip();
        if (base.isEmpty()) base = "Старт";
        base = base.substring(0, Math.min(31, base.length()));
        String candidate = base;
        int suffix = 2;
        while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
            String ending = " (" + suffix++ + ")";
            candidate = base.substring(0, Math.min(base.length(), 31 - ending.length())) + ending;
        }
        return candidate;
    }

    private static String filename(String eventName) {
        String slug = java.text.Normalizer.normalize(eventName, java.text.Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) slug = "event";
        return slug + "-results-template.xlsx";
    }

    private record Styles(
            CellStyle title,
            CellStyle text,
            CellStyle requiredHeader,
            CellStyle optionalHeader,
            CellStyle date,
            CellStyle duration
    ) {}
}
