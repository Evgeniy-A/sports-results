package ru.sportsresults.repository;

import java.time.LocalDate;
import ru.sportsresults.domain.EventPhase;

public record EventCatalogCriteria(
        Integer year,
        Long eventSeriesId,
        String city,
        LocalDate date,
        EventPhase phase
) {
}
