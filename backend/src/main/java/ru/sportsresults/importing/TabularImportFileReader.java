package ru.sportsresults.importing;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class TabularImportFileReader {

    public static final String META_SHEET = "_sports_results_meta";
    public static final String SHEET_RACE_MARKER = "_SPORTS_RESULTS_RACE_ID";
    private static final int MAX_SHEETS = 100;
    private static final int MAX_ROWS = 200_000;
    private static final int MAX_COLUMNS = 256;

    public TabularImportFile read(String filename, byte[] contents) throws IOException {
        return isXlsx(filename, contents) ? readXlsx(contents) : readCsv(contents);
    }

    public ImportFileType type(String filename, byte[] contents) {
        return isXlsx(filename, contents) ? ImportFileType.XLSX : ImportFileType.CSV;
    }

    private static boolean isXlsx(String filename, byte[] contents) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        boolean zipMagic = contents != null && contents.length >= 4
                && contents[0] == 'P' && contents[1] == 'K';
        if (lower.endsWith(".xlsx")) return true;
        if (lower.endsWith(".xls") || lower.endsWith(".xlsm")) {
            throw new TimingCsvFormatException("Only .xlsx Excel files are supported");
        }
        return zipMagic;
    }

    private TabularImportFile readCsv(byte[] contents) throws IOException {
        String text = new String(contents, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        char delimiter = delimiter(text);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .get();
        try (CSVParser parser = format.parse(new StringReader(text))) {
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            validateHeaders(headers, "CSV");
            List<TabularImportFile.Row> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (rows.size() >= MAX_ROWS) throw new TimingCsvFormatException("File exceeds 200000 data rows");
                Map<String, TabularImportFile.Cell> cells = new LinkedHashMap<>();
                for (String header : headers) {
                    String value = record.isMapped(header) ? record.get(header) : "";
                    cells.put(header, new TabularImportFile.Cell(value, null, false, false));
                }
                rows.add(new TabularImportFile.Row(Math.toIntExact(record.getRecordNumber() + 1), cells));
            }
            return new TabularImportFile(
                    ImportFileType.CSV,
                    List.of(new TabularImportFile.Sheet("CSV", headers, rows, null)),
                    null
            );
        }
    }

    private TabularImportFile readXlsx(byte[] contents) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(contents))) {
            if (workbook.getNumberOfSheets() > MAX_SHEETS) {
                throw new TimingCsvFormatException("Workbook exceeds 100 sheets");
            }
            TabularImportFile.TemplateMetadata metadata = metadata(workbook);
            List<TabularImportFile.Sheet> sheets = new ArrayList<>();
            int totalRows = 0;
            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                Sheet sheet = workbook.getSheetAt(index);
                if (sheet.getSheetName().equals(META_SHEET) || sheet.getSheetName().equals("Инструкция")) continue;
                int headerRowIndex = generatedRaceId(sheet) == null ? firstNonEmptyRow(sheet) : 1;
                if (headerRowIndex < 0) continue;
                Row headerRow = sheet.getRow(headerRowIndex);
                int lastColumn = headerRow == null ? 0 : headerRow.getLastCellNum();
                if (lastColumn <= 0 || lastColumn > MAX_COLUMNS) {
                    throw new TimingCsvFormatException("Sheet '" + sheet.getSheetName() + "' has an invalid column count");
                }
                List<String> headers = new ArrayList<>();
                for (int column = 0; column < lastColumn; column++) {
                    String header = cellText(headerRow == null ? null : headerRow.getCell(column));
                    if (header == null || header.isBlank()) header = "_column_" + (column + 1);
                    headers.add(header.strip());
                }
                validateHeaders(headers, "sheet '" + sheet.getSheetName() + "'");
                List<TabularImportFile.Row> rows = new ArrayList<>();
                for (int rowIndex = headerRowIndex + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row source = sheet.getRow(rowIndex);
                    Map<String, TabularImportFile.Cell> cells = new LinkedHashMap<>();
                    boolean any = false;
                    for (int column = 0; column < headers.size(); column++) {
                        TabularImportFile.Cell value = cell(source == null ? null : source.getCell(column));
                        cells.put(headers.get(column), value);
                        any |= !value.blank();
                    }
                    if (!any) continue;
                    if (++totalRows > MAX_ROWS) throw new TimingCsvFormatException("Workbook exceeds 200000 data rows");
                    rows.add(new TabularImportFile.Row(rowIndex + 1, cells));
                }
                sheets.add(new TabularImportFile.Sheet(
                        sheet.getSheetName(), headers, rows, generatedRaceId(sheet)
                ));
            }
            return new TabularImportFile(ImportFileType.XLSX, sheets, metadata);
        } catch (TimingCsvFormatException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TimingCsvFormatException("XLSX workbook could not be read");
        }
    }

    private static TabularImportFile.TemplateMetadata metadata(XSSFWorkbook workbook) {
        Sheet meta = workbook.getSheet(META_SHEET);
        if (meta == null) return null;
        Map<String, String> values = new LinkedHashMap<>();
        for (Row row : meta) {
            String key = cellText(row.getCell(0));
            String value = cellText(row.getCell(1));
            if (key != null && value != null) values.put(key, value);
        }
        try {
            Integer version = Integer.valueOf(values.get("templateFormatVersion"));
            Long eventId = Long.valueOf(values.get("eventId"));
            List<Long> raceIds = values.getOrDefault("raceIds", "").lines()
                    .flatMap(line -> java.util.Arrays.stream(line.split(",")))
                    .map(String::strip).filter(value -> !value.isEmpty()).map(Long::valueOf).sorted().toList();
            return new TabularImportFile.TemplateMetadata(version, eventId, raceIds);
        } catch (RuntimeException exception) {
            throw new TimingCsvFormatException("Sports Results XLSX metadata is invalid");
        }
    }

    private static Long generatedRaceId(Sheet sheet) {
        Row row = sheet.getRow(0);
        if (row == null || !SHEET_RACE_MARKER.equals(cellText(row.getCell(0)))) return null;
        try {
            String value = cellText(row.getCell(1));
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw new TimingCsvFormatException("Sports Results sheet Race metadata is invalid");
        }
    }

    private static int firstNonEmptyRow(Sheet sheet) {
        for (int index = sheet.getFirstRowNum(); index <= sheet.getLastRowNum(); index++) {
            Row row = sheet.getRow(index);
            if (row == null) continue;
            for (Cell cell : row) if (cellText(cell) != null && !cellText(cell).isBlank()) return index;
        }
        return -1;
    }

    private static TabularImportFile.Cell cell(Cell source) {
        if (source == null || source.getCellType() == CellType.BLANK) {
            return new TabularImportFile.Cell(null, null, false, false);
        }
        CellType type = source.getCellType();
        boolean formula = type == CellType.FORMULA;
        if (formula) type = source.getCachedFormulaResultType();
        return switch (type) {
            case NUMERIC -> new TabularImportFile.Cell(
                    null,
                    BigDecimal.valueOf(source.getNumericCellValue()),
                    DateUtil.isCellDateFormatted(source),
                    formula
            );
            case STRING -> new TabularImportFile.Cell(source.getStringCellValue(), null, false, formula);
            case BOOLEAN -> new TabularImportFile.Cell(Boolean.toString(source.getBooleanCellValue()), null, false, formula);
            case ERROR -> new TabularImportFile.Cell(null, null, false, formula);
            default -> new TabularImportFile.Cell(null, null, false, formula);
        };
    }

    private static String cellText(Cell cell) {
        if (cell == null) return null;
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private static void validateHeaders(List<String> headers, String location) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String header : headers) {
            String value = ImportHeaderNormalizer.normalize(header);
            if (value.isEmpty()) continue;
            if (!normalized.add(value)) {
                throw new TimingCsvFormatException("Duplicate header '" + header + "' in " + location);
            }
        }
    }

    private static char delimiter(String text) {
        String first = text.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (char candidate : new char[]{',', ';', '\t'}) {
            int count = 0;
            boolean quoted = false;
            for (int index = 0; index < first.length(); index++) {
                char value = first.charAt(index);
                if (value == '"') quoted = !quoted;
                else if (!quoted && value == candidate) count++;
            }
            counts.put(candidate, count);
        }
        int maximum = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Character> winners = counts.entrySet().stream()
                .filter(entry -> entry.getValue() == maximum && maximum > 0).map(Map.Entry::getKey).toList();
        if (winners.size() != 1) return ',';
        return winners.getFirst();
    }
}
