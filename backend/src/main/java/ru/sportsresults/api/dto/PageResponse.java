package ru.sportsresults.api.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort,
        String direction
) {
    public PageResponse {
        content = List.copyOf(content);
    }
}
