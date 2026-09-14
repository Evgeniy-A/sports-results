package ru.sportsresults.api.dto;

public record ImportErrorDto(Integer row, String field, String message) {
}
