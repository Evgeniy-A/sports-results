package ru.sportsresults.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.domain.PublicResultVisibility;
import ru.sportsresults.domain.StartCluster;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ResultSearchRepositoryImpl implements ResultSearchRepository {

    private final EntityManager entityManager;

    public ResultSearchRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Page<ResultListProjection> search(
            ResultSearchCriteria criteria,
            int page,
            int size,
            ResultSortField sortField,
            Sort.Direction direction
    ) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<ResultListProjection> query = builder.createQuery(ResultListProjection.class);
        Root<Result> result = query.from(Result.class);
        Join<Result, Registration> registration = result.join("registration");
        Join<Registration, Race> race = registration.join("race");
        Join<Registration, Category> category = registration.join("category", JoinType.LEFT);
        Join<Registration, StartCluster> cluster = registration.join("cluster", JoinType.LEFT);

        query.select(builder.construct(
                ResultListProjection.class,
                registration.get("id"),
                result.get("id"),
                race.get("id"),
                race.get("name"),
                registration.get("displayName"),
                registration.get("firstName"),
                registration.get("lastName"),
                registration.get("bib"),
                registration.get("gender"),
                registration.get("sourceCategory"),
                cluster.get("id"),
                cluster.get("displayName"),
                registration.get("entryKind"),
                category.get("id"),
                category.get("sourceName"),
                category.get("displayName"),
                result.get("status"),
                result.get("gunTime"),
                result.get("chipTime"),
                result.get("overallPlace"),
                result.get("genderPlace"),
                result.get("categoryPlace"),
                result.get("netOverallPlace"),
                result.get("netGenderPlace"),
                result.get("netCategoryPlace"),
                contextualPlace(builder, result, race, criteria),
                race.get("publicRankingBasis")
        ));
        query.where(predicates(builder, criteria, registration, race, category, result));
        query.orderBy(orders(builder, result, registration, race, criteria, sortField, direction));

        TypedQuery<ResultListProjection> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult(Math.multiplyExact(page, size));
        typedQuery.setMaxResults(size);

        long total = count(builder, criteria);
        return new PageImpl<>(typedQuery.getResultList(), PageRequest.of(page, size), total);
    }

    private long count(CriteriaBuilder builder, ResultSearchCriteria criteria) {
        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<Result> result = countQuery.from(Result.class);
        Join<Result, Registration> registration = result.join("registration");
        Join<Registration, Race> race = registration.join("race");
        Join<Registration, Category> category = registration.join("category", JoinType.LEFT);
        countQuery.select(builder.count(result));
        countQuery.where(predicates(builder, criteria, registration, race, category, result));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private Predicate[] predicates(
            CriteriaBuilder builder,
            ResultSearchCriteria criteria,
            Join<Result, Registration> registration,
            Join<Registration, Race> race,
            Join<Registration, Category> category,
            Root<Result> result
    ) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(builder.equal(race.get("event").get("id"), criteria.eventId()));
        predicates.add(builder.isNull(registration.get("retiredAt")));
        if (criteria.publicOnly()) {
            predicates.add(builder.lower(result.get("status")).in(PublicResultVisibility.publicStatuses()));
        }
        if (criteria.raceId() != null) {
            predicates.add(builder.equal(race.get("id"), criteria.raceId()));
        }
        if (criteria.categoryId() != null) {
            predicates.add(builder.equal(category.get("id"), criteria.categoryId()));
        }
        if (!criteria.excludedResultIds().isEmpty()) {
            predicates.add(builder.not(result.get("id").in(criteria.excludedResultIds())));
        }
        if (criteria.clusterId() != null) {
            predicates.add(builder.equal(registration.get("cluster").get("id"), criteria.clusterId()));
        }
        if (hasText(criteria.bib())) {
            predicates.add(builder.equal(registration.get("bib"), criteria.bib().strip()));
        }
        if (hasText(criteria.gender())) {
            predicates.add(builder.equal(
                    builder.lower(registration.get("gender")),
                    criteria.gender().strip().toLowerCase(Locale.ROOT)
            ));
        }
        if (hasText(criteria.status())) {
            predicates.add(builder.equal(
                    builder.lower(result.get("status")),
                    criteria.status().strip().toLowerCase(Locale.ROOT)
            ));
        }
        if (hasText(criteria.name())) {
            String pattern = "%" + escapeLike(criteria.name().strip().toLowerCase(Locale.ROOT)) + "%";
            predicates.add(builder.like(registration.get("searchText"), pattern, '\\'));
        }
        return predicates.toArray(Predicate[]::new);
    }

    private List<Order> orders(
            CriteriaBuilder builder,
            Root<Result> result,
            Join<Result, Registration> registration,
            Join<Registration, Race> race,
            ResultSearchCriteria criteria,
            ResultSortField sortField,
            Sort.Direction direction
    ) {
        List<Order> orders = new ArrayList<>();
        if (criteria.publicOnly()) {
            Expression<Integer> finishGroup = builder.<Integer>selectCase()
                    .when(builder.equal(builder.lower(result.<String>get("status")), "finished"), 0)
                    .otherwise(1);
            orders.add(builder.asc(finishGroup));
        }
        switch (sortField) {
            case CONTEXT_PLACE -> addNullsLast(orders, builder, contextualPlace(builder, result, race, criteria), direction);
            case CONTEXT_TIME -> addNullsLast(orders, builder, contextualTime(builder, result, race, criteria), direction);
            case OVERALL_PLACE -> addNullsLast(orders, builder, result.<Integer>get("overallPlace"), direction);
            case NET_OVERALL_PLACE -> addNullsLast(orders, builder, result.<Integer>get("netOverallPlace"), direction);
            case GUN_TIME -> addNullsLast(orders, builder, result.<Duration>get("gunTime"), direction);
            case CHIP_TIME -> addNullsLast(orders, builder, result.<Duration>get("chipTime"), direction);
            case DISPLAY_NAME -> addNameOrder(orders, builder, registration, direction);
            case BIB -> addBibOrder(orders, builder, registration, direction);
        }
        orders.add(builder.asc(result.get("id")));
        return orders;
    }

    private void addBibOrder(
            List<Order> orders,
            CriteriaBuilder builder,
            Join<Result, Registration> registration,
            Sort.Direction direction
    ) {
        Expression<String> bib = registration.get("bib");
        Expression<String> withoutDigits = builder.function(
                "regexp_replace", String.class, bib,
                builder.literal("[0-9]"), builder.literal(""), builder.literal("g")
        );
        Predicate numeric = builder.and(
                builder.isNotNull(bib),
                builder.notEqual(bib, ""),
                builder.equal(withoutDigits, "")
        );
        Expression<Integer> bibGroup = builder.<Integer>selectCase()
                .when(numeric, 0)
                .when(builder.isNotNull(bib), 1)
                .otherwise(2);

        Expression<String> withoutLeadingZeros = builder.function(
                "ltrim", String.class, bib, builder.literal("0")
        );
        Expression<String> normalizedNumeric = builder.<String>selectCase()
                .when(builder.equal(withoutLeadingZeros, ""), "0")
                .otherwise(withoutLeadingZeros);
        Expression<Integer> numericLength = builder.<Integer>selectCase()
                .when(numeric, builder.length(normalizedNumeric))
                .otherwise(builder.nullLiteral(Integer.class));
        Expression<String> numericValue = builder.<String>selectCase()
                .when(numeric, normalizedNumeric)
                .otherwise(builder.nullLiteral(String.class));
        Expression<String> nonNumericValue = builder.<String>selectCase()
                .when(builder.and(builder.isNotNull(bib), builder.not(numeric)), builder.lower(bib))
                .otherwise(builder.nullLiteral(String.class));

        // Numeric BIBs always precede textual BIBs; NULL remains last in both directions.
        // Length plus normalized digits is an overflow-safe numeric ordering for VARCHAR(64).
        orders.add(builder.asc(bibGroup));
        orders.add(direction.isAscending() ? builder.asc(numericLength) : builder.desc(numericLength));
        orders.add(direction.isAscending() ? builder.asc(numericValue) : builder.desc(numericValue));
        orders.add(direction.isAscending() ? builder.asc(nonNumericValue) : builder.desc(nonNumericValue));
        orders.add(direction.isAscending() ? builder.asc(bib) : builder.desc(bib));
    }

    private Expression<Integer> contextualPlace(
            CriteriaBuilder builder,
            Root<Result> result,
            Join<Registration, Race> race,
            ResultSearchCriteria criteria
    ) {
        String gunField;
        String chipField;
        if (criteria.categoryId() != null) {
            gunField = "categoryPlace";
            chipField = "netCategoryPlace";
        } else if (hasText(criteria.gender())) {
            gunField = "genderPlace";
            chipField = "netGenderPlace";
        } else {
            gunField = "overallPlace";
            chipField = "netOverallPlace";
        }
        if (criteria.rankingBasis() != null) {
            return switch (criteria.rankingBasis()) {
                case CHIP_TIME -> result.get(chipField);
                case GUN_TIME -> result.get(gunField);
                case NONE -> builder.nullLiteral(Integer.class);
            };
        }
        return builder.<Integer>selectCase()
                .when(builder.equal(race.get("publicRankingBasis"), RankingBasis.CHIP_TIME), result.get(chipField))
                .otherwise(result.get(gunField));
    }

    private Expression<Duration> contextualTime(
            CriteriaBuilder builder,
            Root<Result> result,
            Join<Registration, Race> race,
            ResultSearchCriteria criteria
    ) {
        if (criteria.rankingBasis() != null) {
            return switch (criteria.rankingBasis()) {
                case CHIP_TIME -> result.get("chipTime");
                case GUN_TIME, NONE -> result.get("gunTime");
            };
        }
        return builder.<Duration>selectCase()
                .when(builder.equal(race.get("publicRankingBasis"), RankingBasis.CHIP_TIME), result.get("chipTime"))
                .otherwise(result.get("gunTime"));
    }

    private void addNameOrder(
            List<Order> orders,
            CriteriaBuilder builder,
            Join<Result, Registration> registration,
            Sort.Direction direction
    ) {
        Expression<String> name = builder.lower(registration.get("displayName"));
        Expression<String> first = builder.function("left", String.class, name, builder.literal(1));
        Expression<Integer> cyrillicPosition = builder.function(
                "strpos", Integer.class, builder.literal("абвгдеёжзийклмнопрстуфхцчшщъыьэюя"), first
        );
        Expression<Integer> latinPosition = builder.function(
                "strpos", Integer.class, builder.literal("abcdefghijklmnopqrstuvwxyz"), first
        );
        Expression<Integer> scriptGroup = builder.<Integer>selectCase()
                .when(builder.greaterThan(cyrillicPosition, 0), 0)
                .when(builder.greaterThan(latinPosition, 0), 1)
                .otherwise(2);
        Expression<String> normalized = builder.function(
                "replace", String.class, name, builder.literal("ё"), builder.literal("е")
        );
        orders.add(builder.asc(scriptGroup));
        orders.add(direction.isAscending() ? builder.asc(normalized) : builder.desc(normalized));
    }

    private <T extends Comparable<? super T>> void addNullsLast(
            List<Order> orders,
            CriteriaBuilder builder,
            Expression<T> expression,
            Sort.Direction direction
    ) {
        if (direction.isAscending()) {
            // PostgreSQL orders NULL values last for ASC, allowing its B-tree indexes to serve pagination.
            orders.add(builder.asc(expression));
        } else {
            orders.add(builder.asc(builder.isNull(expression)));
            orders.add(builder.desc(expression));
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
