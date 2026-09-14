package ru.sportsresults.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.ResultIssueAttachment;
import ru.sportsresults.domain.ResultIssueRequest;
import ru.sportsresults.domain.ResultIssueQueueScope;
import ru.sportsresults.domain.SportFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ResultIssueRequestSearchRepositoryImpl implements ResultIssueRequestSearchRepository {

    private final EntityManager entityManager;

    public ResultIssueRequestSearchRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<AdminResultIssueListProjection> searchAdmin(
            ResultIssueRequestSearchCriteria criteria,
            int page,
            int size
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AdminResultIssueListProjection> query =
                builder.createQuery(AdminResultIssueListProjection.class);
        Root<ResultIssueRequest> issue = query.from(ResultIssueRequest.class);
        Join<ResultIssueRequest, Registration> registration = issue.join("registration");
        Join<Registration, Race> race = registration.join("race");
        Join<Race, SportFormat> sportFormat = race.join("sportFormat");
        Join<ResultIssueRequest, Result> result = issue.join("result", JoinType.LEFT);

        Subquery<Long> attachmentCount = query.subquery(Long.class);
        Root<ResultIssueRequest> correlatedIssue = attachmentCount.correlate(issue);
        Root<ResultIssueAttachment> attachment = attachmentCount.from(ResultIssueAttachment.class);
        attachmentCount.select(builder.count(attachment));
        attachmentCount.where(builder.equal(attachment.get("issueRequest"), correlatedIssue));

        query.select(builder.construct(
                AdminResultIssueListProjection.class,
                issue.get("id"),
                issue.get("status"),
                issue.get("issueType"),
                issue.get("correctionReason"),
                issue.get("createdAt"),
                issue.get("queueArchivedAt"),
                registration.get("id"),
                registration.get("bib"),
                registration.get("displayName"),
                race.get("id"),
                race.get("name"),
                sportFormat.get("id"),
                sportFormat.get("displayName"),
                result.get("id"),
                attachmentCount
        ));
        query.where(predicates(builder, criteria, issue, registration));
        query.orderBy(builder.asc(issue.get("id")));

        TypedQuery<AdminResultIssueListProjection> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(Math.multiplyExact(page, size));
        typedQuery.setMaxResults(size);

        return new PageImpl<>(
                typedQuery.getResultList(),
                PageRequest.of(page, size),
                count(builder, criteria)
        );
    }

    @Override
    public Page<GlobalResultIssueJournalProjection> searchJournal(
            ResultIssueJournalFilter filter,
            int page,
            int size,
            ResultIssueJournalSort sort,
            Sort.Direction direction
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<GlobalResultIssueJournalProjection> query =
                builder.createQuery(GlobalResultIssueJournalProjection.class);
        Root<ResultIssueRequest> issue = query.from(ResultIssueRequest.class);

        Subquery<Long> attachmentCount = query.subquery(Long.class);
        Root<ResultIssueRequest> correlatedIssue = attachmentCount.correlate(issue);
        Root<ResultIssueAttachment> attachment = attachmentCount.from(ResultIssueAttachment.class);
        attachmentCount.select(builder.count(attachment));
        attachmentCount.where(builder.equal(attachment.get("issueRequest"), correlatedIssue));

        query.select(builder.construct(
                GlobalResultIssueJournalProjection.class,
                issue.get("id"),
                issue.get("issueType"),
                issue.get("correctionReason"),
                issue.get("status"),
                issue.get("createdAt"),
                issue.get("updatedAt"),
                issue.get("queueArchivedAt"),
                issue.get("queueArchivedBy"),
                issue.get("queueArchiveReason"),
                issue.get("queueArchivedImportOperationId"),
                issue.get("event").get("id"),
                issue.get("snapshotEventName"),
                issue.get("snapshotEventLocation"),
                issue.get("snapshotEventStartsAt"),
                issue.get("snapshotSportFormatId"),
                issue.get("snapshotSportFormatName"),
                issue.get("snapshotSportFormatCode"),
                issue.get("snapshotRaceId"),
                issue.get("snapshotRaceName"),
                issue.get("snapshotRaceCode"),
                issue.get("snapshotRaceDistanceMeters"),
                issue.get("registration").get("id"),
                issue.get("snapshotBib"),
                issue.get("snapshotDisplayName"),
                issue.get("snapshotEffectiveCategoryName"),
                issue.get("observedResultStatus"),
                issue.get("observedGunTime"),
                issue.get("observedChipTime"),
                issue.get("snapshotOrigin"),
                attachmentCount
        ));
        query.where(journalPredicates(builder, filter, issue));
        applyJournalOrder(builder, query, issue, sort, direction);

        TypedQuery<GlobalResultIssueJournalProjection> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(Math.multiplyExact(page, size));
        typedQuery.setMaxResults(size);
        return new PageImpl<>(
                typedQuery.getResultList(),
                PageRequest.of(page, size),
                countJournal(filter)
        );
    }

    @Override
    public long countJournal(ResultIssueJournalFilter filter) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<ResultIssueRequest> issue = query.from(ResultIssueRequest.class);
        query.select(builder.count(issue));
        query.where(journalPredicates(builder, filter, issue));
        return entityManager.createQuery(query).getSingleResult();
    }

    @Override
    public List<GlobalResultIssueExportProjection> findJournalExportBatch(
            ResultIssueJournalFilter filter,
            int offset,
            int size,
            ResultIssueJournalSort sort,
            Sort.Direction direction
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<GlobalResultIssueExportProjection> query =
                builder.createQuery(GlobalResultIssueExportProjection.class);
        Root<ResultIssueRequest> issue = query.from(ResultIssueRequest.class);
        Join<ResultIssueRequest, Registration> registration = issue.join("registration");
        Join<Registration, Race> currentRace = registration.join("race");
        Join<Registration, Category> currentCategory = registration.join("category", JoinType.LEFT);

        query.select(builder.construct(
                GlobalResultIssueExportProjection.class,
                issue.get("id"),
                issue.get("snapshotEventName"),
                issue.get("snapshotEventLocation"),
                issue.get("snapshotEventStartsAt"),
                issue.get("snapshotSportFormatName"),
                issue.get("snapshotRaceName"),
                issue.get("snapshotRaceDistanceMeters"),
                issue.get("snapshotBib"),
                issue.get("snapshotDisplayName"),
                issue.get("snapshotEffectiveCategoryName"),
                issue.get("snapshotSourceCategory"),
                issue.get("issueType"),
                issue.get("correctionReason"),
                issue.get("status"),
                issue.get("queueArchivedAt"),
                issue.get("queueArchiveReason"),
                issue.get("createdAt"),
                issue.get("updatedAt"),
                issue.get("observedResultStatus"),
                issue.get("observedGunTime"),
                issue.get("observedChipTime"),
                issue.get("contactEmail"),
                issue.get("message"),
                issue.get("claimedGunTime"),
                issue.get("claimedChipTime"),
                issue.get("estimatedStartAt"),
                issue.get("estimatedFinishAt"),
                issue.get("snapshotOrigin"),
                currentRace.get("name"),
                currentCategory.get("displayName"),
                registration.get("retiredAt")
        ));
        query.where(journalPredicates(builder, filter, issue));
        applyJournalOrder(builder, query, issue, sort, direction);

        TypedQuery<GlobalResultIssueExportProjection> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(offset);
        typedQuery.setMaxResults(size);
        return typedQuery.getResultList();
    }

    private long count(CriteriaBuilder builder, ResultIssueRequestSearchCriteria criteria) {
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<ResultIssueRequest> issue = query.from(ResultIssueRequest.class);
        Join<ResultIssueRequest, Registration> registration = issue.join("registration");
        query.select(builder.count(issue));
        query.where(predicates(builder, criteria, issue, registration));
        return entityManager.createQuery(query).getSingleResult();
    }

    private static Predicate[] journalPredicates(
            CriteriaBuilder builder,
            ResultIssueJournalFilter filter,
            Root<ResultIssueRequest> issue
    ) {
        List<Predicate> predicates = new ArrayList<>();
        if (filter.issueId() != null) {
            predicates.add(builder.equal(issue.get("id"), filter.issueId()));
        }
        if (filter.eventId() != null) {
            predicates.add(builder.equal(issue.get("event").get("id"), filter.eventId()));
        }
        if (filter.location() != null) {
            predicates.add(builder.like(
                    builder.lower(issue.<String>get("snapshotEventLocation")),
                    containsPattern(filter.location()),
                    '\\'
            ));
        }
        if (filter.eventStartsAtFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(
                    issue.get("snapshotEventStartsAt"), filter.eventStartsAtFrom()
            ));
        }
        if (filter.eventStartsAtToExclusive() != null) {
            predicates.add(builder.lessThan(
                    issue.get("snapshotEventStartsAt"), filter.eventStartsAtToExclusive()
            ));
        }
        if (filter.createdFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(issue.get("createdAt"), filter.createdFrom()));
        }
        if (filter.createdTo() != null) {
            predicates.add(builder.lessThanOrEqualTo(issue.get("createdAt"), filter.createdTo()));
        }
        if (filter.sportFormatId() != null) {
            predicates.add(builder.equal(issue.get("snapshotSportFormatId"), filter.sportFormatId()));
        }
        if (filter.sportFormatCode() != null) {
            predicates.add(builder.equal(
                    builder.lower(issue.<String>get("snapshotSportFormatCode")),
                    filter.sportFormatCode().toLowerCase(Locale.ROOT)
            ));
        }
        if (filter.raceId() != null) {
            predicates.add(builder.equal(issue.get("snapshotRaceId"), filter.raceId()));
        }
        if (filter.raceCode() != null) {
            predicates.add(builder.equal(
                    builder.lower(issue.<String>get("snapshotRaceCode")),
                    filter.raceCode().toLowerCase(Locale.ROOT)
            ));
        }
        if (filter.bib() != null) {
            predicates.add(builder.equal(issue.get("snapshotBib"), filter.bib()));
        }
        if (filter.participant() != null) {
            predicates.add(builder.like(
                    builder.lower(issue.<String>get("snapshotDisplayName")),
                    containsPattern(filter.participant()),
                    '\\'
            ));
        }
        if (filter.issueType() != null) {
            predicates.add(builder.equal(issue.get("issueType"), filter.issueType()));
        }
        if (filter.correctionReason() != null) {
            predicates.add(builder.equal(issue.get("correctionReason"), filter.correctionReason()));
        }
        if (!filter.statuses().isEmpty()) {
            predicates.add(issue.get("status").in(filter.statuses()));
        }
        if (filter.queueScope() == ResultIssueQueueScope.CURRENT) {
            predicates.add(builder.isNull(issue.get("queueArchivedAt")));
        } else if (filter.queueScope() == ResultIssueQueueScope.ARCHIVED) {
            predicates.add(builder.isNotNull(issue.get("queueArchivedAt")));
        }
        if (filter.queueArchiveReason() != null) {
            predicates.add(builder.equal(issue.get("queueArchiveReason"), filter.queueArchiveReason()));
        }
        if (filter.queueArchivedImportOperationId() != null) {
            predicates.add(builder.equal(
                    issue.get("queueArchivedImportOperationId"), filter.queueArchivedImportOperationId()
            ));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private static String containsPattern(String value) {
        String normalized = value.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + normalized + "%";
    }

    private static void applyJournalOrder(
            CriteriaBuilder builder,
            CriteriaQuery<?> query,
            Root<ResultIssueRequest> issue,
            ResultIssueJournalSort sort,
            Sort.Direction direction
    ) {
        var primaryOrder = direction.isAscending()
                ? builder.asc(issue.get(sort.entityAttribute()))
                : builder.desc(issue.get(sort.entityAttribute()));
        if (sort == ResultIssueJournalSort.ISSUE_ID) {
            query.orderBy(primaryOrder);
        } else {
            query.orderBy(
                    primaryOrder,
                    direction.isAscending() ? builder.asc(issue.get("id")) : builder.desc(issue.get("id"))
            );
        }
    }

    private static Predicate[] predicates(
            CriteriaBuilder builder,
            ResultIssueRequestSearchCriteria criteria,
            Root<ResultIssueRequest> issue,
            Join<ResultIssueRequest, Registration> registration
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(issue.get("event").get("id"), criteria.eventId()));
        if (criteria.queueScope() == ResultIssueQueueScope.CURRENT) {
            predicates.add(builder.isNull(issue.get("queueArchivedAt")));
        } else if (criteria.queueScope() == ResultIssueQueueScope.ARCHIVED) {
            predicates.add(builder.isNotNull(issue.get("queueArchivedAt")));
        }
        if (criteria.status() != null) {
            predicates.add(builder.equal(issue.get("status"), criteria.status()));
        }
        if (criteria.issueType() != null) {
            predicates.add(builder.equal(issue.get("issueType"), criteria.issueType()));
        }
        if (criteria.issueIdFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(issue.get("id"), criteria.issueIdFrom()));
        }
        if (criteria.issueIdTo() != null) {
            predicates.add(builder.lessThanOrEqualTo(issue.get("id"), criteria.issueIdTo()));
        }
        if (criteria.bib() != null) {
            predicates.add(builder.equal(registration.get("bib"), criteria.bib()));
        }
        if (criteria.correctionReason() != null) {
            predicates.add(builder.equal(issue.get("correctionReason"), criteria.correctionReason()));
        }
        return predicates.toArray(Predicate[]::new);
    }
}
