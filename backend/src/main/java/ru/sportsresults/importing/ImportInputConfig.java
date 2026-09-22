package ru.sportsresults.importing;

import java.util.LinkedHashMap;
import java.util.Map;

public record ImportInputConfig(
        Long targetRaceId,
        Map<String, CanonicalImportField> columnMappings,
        Map<String, Long> raceMappings,
        boolean saveRaceMappings
) {
    public ImportInputConfig {
        columnMappings = columnMappings == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(columnMappings));
        raceMappings = raceMappings == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(raceMappings));
    }

    public static ImportInputConfig legacy() {
        return new ImportInputConfig(null, Map.of(), Map.of(), false);
    }
}
