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
import java.util.UUID;

final class ManualResultCorrectionFields {

    private static final String ACCEPTED_SOURCE_PREFIX = "importAcceptedSource:";

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
        changes.forEach(change -> {
            if (SUPPORTED_FIELDS.contains(change.getFieldName())) {
                latestByField.put(change.getFieldName(), change);
                return;
            }
            String acceptedField = acceptedSourceField(change.getFieldName());
            if (acceptedField != null) {
                latestByField.remove(acceptedField);
            }
        });

        Set<String> active = new LinkedHashSet<>();
        SUPPORTED_FIELDS.stream().sorted().forEach(field -> {
            AdminChangeLog latest = latestByField.get(field);
            if (latest != null && Objects.equals(latest.getNewValue(), currentValue(result, field))) {
                active.add(field);
            }
        });
        return Set.copyOf(active);
    }

    static String acceptedSourceAuditField(UUID operationId, String field) {
        if (!SUPPORTED_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Unsupported Result field: " + field);
        }
        return ACCEPTED_SOURCE_PREFIX + field + ":" + operationId;
    }

    private static String acceptedSourceField(String fieldName) {
        if (fieldName == null || !fieldName.startsWith(ACCEPTED_SOURCE_PREFIX)) {
            return null;
        }
        String remainder = fieldName.substring(ACCEPTED_SOURCE_PREFIX.length());
        int separator = remainder.indexOf(':');
        if (separator <= 0 || separator == remainder.length() - 1) {
            return null;
        }
        String field = remainder.substring(0, separator);
        return SUPPORTED_FIELDS.contains(field) ? field : null;
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
