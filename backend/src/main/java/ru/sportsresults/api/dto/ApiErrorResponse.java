package ru.sportsresults.api.dto;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        List<FieldViolation> violations
) {
    public ApiErrorResponse {
        violations = List.copyOf(violations);
    }

    public record FieldViolation(String field, String message) {
    }
}
