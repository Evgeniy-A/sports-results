package ru.sportsresults.api.dto;

import java.util.List;

public record AdminUserEventAccessDto(Long userId, List<Long> eventIds) {
}
