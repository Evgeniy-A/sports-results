package ru.sportsresults.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.sportsresults.domain.Event;
import ru.sportsresults.domain.EventPublicationStatus;
import ru.sportsresults.domain.EventSeries;
import ru.sportsresults.domain.ResultsPublicationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import ru.sportsresults.service.EventPhaseCalculator;

public class EventCatalogRepositoryImpl implements EventCatalogRepository {

    private final EntityManager entityManager;

    public EventCatalogRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<Event> searchPublished(EventCatalogCriteria criteria, int page, int size) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = builder.createQuery(Event.class);
        Root<Event> event = query.from(Event.class);
        event.fetch("eventSeries");
        Join<Event, EventSeries> series = event.join("eventSeries");
        query.select(event).where(predicates(builder, criteria, event, series));
        query.orderBy(builder.asc(builder.isNull(event.get("startsAt"))), builder.desc(event.get("startsAt")), builder.desc(event.get("id")));
        Instant now = Instant.now();
        List<Event> filtered = entityManager.createQuery(query).getResultList().stream()
                .filter(candidate -> matchesLocalDateFilters(candidate, criteria))
                .filter(candidate -> criteria.phase() == null
                        || EventPhaseCalculator.calculate(candidate, now) == criteria.phase())
                .toList();
        int from = Math.min(Math.multiplyExact(page, size), filtered.size());
        int to = Math.min(from + size, filtered.size());
        return new PageImpl<>(filtered.subList(from, to), PageRequest.of(page, size), filtered.size());
    }

    @Override
    public Page<Event> searchAdmin(
            String name,
            String location,
            EventPublicationStatus publicationStatus,
            ResultsPublicationStatus resultsPublicationStatus,
            Pageable pageable
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Event> query = builder.createQuery(Event.class);
        Root<Event> event = query.from(Event.class);
        event.fetch("eventSeries");
        query.select(event).where(adminPredicates(
                builder, event, name, location, publicationStatus, resultsPublicationStatus
        ));
        query.orderBy(builder.asc(builder.isNull(event.get("startsAt"))),
                builder.desc(event.get("startsAt")), builder.desc(event.get("id")));
        List<Event> content = entityManager.createQuery(query)
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize())
                .getResultList();

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Event> countEvent = countQuery.from(Event.class);
        countQuery.select(builder.count(countEvent)).where(adminPredicates(
                builder, countEvent, name, location, publicationStatus, resultsPublicationStatus
        ));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(content, pageable, total);
    }

    private Predicate[] adminPredicates(
            CriteriaBuilder builder,
            Root<Event> event,
            String name,
            String location,
            EventPublicationStatus publicationStatus,
            ResultsPublicationStatus resultsPublicationStatus
    ) {
        List<Predicate> predicates = new ArrayList<>();
        if (name != null && !name.isBlank()) {
            predicates.add(builder.like(
                    builder.lower(event.get("name")),
                    "%" + name.strip().toLowerCase(Locale.ROOT) + "%"
            ));
        }
        if (location != null && !location.isBlank()) {
            predicates.add(builder.like(
                    builder.lower(event.get("location")),
                    "%" + location.strip().toLowerCase(Locale.ROOT) + "%"
            ));
        }
        if (publicationStatus != null) {
            predicates.add(builder.equal(event.get("publicationStatus"), publicationStatus));
        }
        if (resultsPublicationStatus != null) {
            predicates.add(builder.equal(event.get("resultsPublicationStatus"), resultsPublicationStatus));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private Predicate[] predicates(
            CriteriaBuilder builder,
            EventCatalogCriteria criteria,
            Root<Event> event,
            Join<Event, EventSeries> series
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(event.get("publicationStatus"), EventPublicationStatus.PUBLISHED));
        predicates.add(builder.isTrue(series.get("active")));
        if (criteria.eventSeriesId() != null) {
            predicates.add(builder.equal(series.get("id"), criteria.eventSeriesId()));
        }
        if (criteria.city() != null && !criteria.city().isBlank()) {
            predicates.add(builder.equal(builder.lower(event.get("location")), criteria.city().strip().toLowerCase(Locale.ROOT)));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private boolean matchesLocalDateFilters(Event event, EventCatalogCriteria criteria) {
        if (criteria.year() == null && criteria.date() == null) {
            return true;
        }
        if (event.getStartsAt() == null) {
            return false;
        }
        ZoneId zone = ZoneId.of(event.getTimeZone());
        LocalDate start = event.getStartsAt().atZone(zone).toLocalDate();
        LocalDate end = (event.getEndsAt() == null ? event.getStartsAt() : event.getEndsAt())
                .atZone(zone)
                .toLocalDate();
        if (criteria.year() != null && start.getYear() != criteria.year()) {
            return false;
        }
        return criteria.date() == null
                || (!criteria.date().isBefore(start) && !criteria.date().isAfter(end));
    }
}
