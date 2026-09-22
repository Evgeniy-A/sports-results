package ru.sportsresults.importing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record TabularImportFile(
        ImportFileType fileType,
        List<Sheet> sheets,
        TemplateMetadata templateMetadata
) {
    public TabularImportFile {
        sheets = List.copyOf(sheets);
    }

    public record Sheet(String name, List<String> headers, List<Row> rows, Long metadataRaceId) {
        public Sheet {
            headers = List.copyOf(headers);
            rows = List.copyOf(rows);
        }
    }

    public record Row(int rowNumber, Map<String, Cell> cells) {
        public Row { cells = Map.copyOf(cells); }
    }

    public record Cell(String text, BigDecimal numericValue, boolean dateFormatted, boolean formula) {
        public boolean blank() { return (text == null || text.isBlank()) && numericValue == null; }
    }

    public record TemplateMetadata(Integer formatVersion, Long eventId, List<Long> raceIds) {
        public TemplateMetadata { raceIds = raceIds == null ? List.of() : List.copyOf(raceIds); }
    }
}
