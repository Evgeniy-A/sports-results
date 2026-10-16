package ru.sportsresults.service;

import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.Result;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ManualResultCorrectionFields {

    static final Set<String> SUPPORTED_FIELDS = Set.of(
            "status",
            "gunTimeMs",
            "chipTimeMs",
            "overallPlace",
            "genderPlace",
            "categoryPlace",
            "netOverallPlace",
            "netGenderPlace",
            "netCategoryPlace"
    );

    private ManualResultCorrectionFields() {
    }

    static Set<String> active(Result result, List<AdminChangeLog> changes) {
        Map<String, AdminChangeLog> latestByField = new LinkedHashMap<>();
        changes.stream()
                .filter(change -> SUPPORTED_FIELDS.contains(change.getFieldName()))
                .forEach(change -> latestByField.put(change.getFieldName(), change));

        Set<String> active = new LinkedHashSet<>();
        SUPPORTED_FIELDS.stream().sorted().forEach(field -> {
            AdminChangeLog latest = latestByField.get(field);
            if (latest != null && Objects.equals(latest.getNewValue(), currentValue(result, field))) {
                active.add(field);
            }
        });
        return Set.copyOf(active);
    }

    private static String currentValue(Result result, String field) {
        return switch (field) {
            case "status" -> result.getStatus();
            case "gunTimeMs" -> milliseconds(result.getGunTime());
            case "chipTimeMs" -> milliseconds(result.getChipTime());
            case "overallPlace" -> text(result.getOverallPlace());
            case "genderPlace" -> text(result.getGenderPlace());
            case "categoryPlace" -> text(result.getCategoryPlace());
            case "netOverallPlace" -> text(result.getNetOverallPlace());
            case "netGenderPlace" -> text(result.getNetGenderPlace());
            case "netCategoryPlace" -> text(result.getNetCategoryPlace());
            default -> null;
        };
    }

    private static String milliseconds(Duration value) {
        return value == null ? null : Long.toString(value.toMillis());
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}
