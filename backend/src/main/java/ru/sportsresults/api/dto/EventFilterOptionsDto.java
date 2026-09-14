package ru.sportsresults.api.dto;

import java.util.List;

public record EventFilterOptionsDto(
        List<Integer> years,
        List<EventSeriesOptionDto> eventSeries,
        List<String> cities
) {
    public EventFilterOptionsDto {
        years = List.copyOf(years);
        eventSeries = List.copyOf(eventSeries);
        cities = List.copyOf(cities);
    }
}
