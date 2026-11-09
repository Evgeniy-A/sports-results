package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.PublicAppealEventDto;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.EventPublicationStatus;
import ru.sportsresults.domain.ResultInquiryAvailability;
import ru.sportsresults.repository.EventRepository;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PublicAppealCatalogService {

    private final EventRepository eventRepository;
    private final ResultInquiryAvailabilityService availabilityService;

    public PublicAppealCatalogService(
            EventRepository eventRepository,
            ResultInquiryAvailabilityService availabilityService
    ) {
        this.eventRepository = eventRepository;
        this.availabilityService = availabilityService;
    }

    @Transactional(readOnly = true)
    public List<PublicAppealEventDto> openEvents() {
        List<Event> open = eventRepository
                .findAllByPublicationStatusOrderByStartsAtDesc(EventPublicationStatus.PUBLISHED)
                .stream()
                .filter(event -> availabilityService.calculate(event).state() == ResultInquiryAvailability.OPEN)
                .filter(event -> event.getStartsAt() != null)
                .toList();

        Map<GroupKey, List<Event>> grouped = new LinkedHashMap<>();
        open.forEach(event -> grouped.computeIfAbsent(
                new GroupKey(event.getEventSeries().getId(), localYear(event)), ignored -> new ArrayList<>()
        ).add(event));

        return grouped.entrySet().stream()
                .map(entry -> toDto(entry.getKey(), entry.getValue()))
                .sorted(Comparator
                        .comparing(PublicAppealCatalogService::latestDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(PublicAppealEventDto::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static PublicAppealEventDto toDto(GroupKey key, List<Event> events) {
        Event first = events.getFirst();
        Map<String, Event> byCity = new LinkedHashMap<>();
        events.stream()
                .sorted(Comparator.comparing(Event::getStartsAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(event -> byCity.putIfAbsent(normalizeCityKey(event.getLocation()), event));
        List<PublicAppealEventDto.City> cities = byCity.values().stream()
                .map(event -> new PublicAppealEventDto.City(
                        cityLabel(event.getLocation()),
                        event.getSlug(),
                        event.getName(),
                        event.getStartsAt(),
                        event.getTimeZone()
                ))
                .sorted(Comparator.comparing(PublicAppealEventDto.City::city, String.CASE_INSENSITIVE_ORDER))
                .toList();
        String title = first.getEventSeries().getName() + " " + key.year();
        return new PublicAppealEventDto(
                first.getEventSeries().getSlug() + ":" + key.year(), title, key.year(), cities
        );
    }

    private static Instant latestDate(PublicAppealEventDto event) {
        return event.cities().stream().map(PublicAppealEventDto.City::eventDate)
                .filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
    }

    private static int localYear(Event event) {
        return event.getStartsAt().atZone(ZoneId.of(event.getTimeZone())).getYear();
    }

    private static String cityLabel(String value) {
        if (value == null || value.isBlank()) {
            return "Город не указан";
        }
        return value.strip().replaceAll("\\s+", " ");
    }

    private static String normalizeCityKey(String value) {
        return cityLabel(value).toLowerCase(Locale.ROOT);
    }

    private record GroupKey(Long seriesId, int year) {
    }
}
