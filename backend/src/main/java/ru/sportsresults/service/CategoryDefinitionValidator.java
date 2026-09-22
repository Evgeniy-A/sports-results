package ru.sportsresults.service;

import ru.sportsresults.domain.CategoryGender;

import java.util.Collection;

final class CategoryDefinitionValidator {

    private CategoryDefinitionValidator() {
    }

    static void validate(Rule candidate, Collection<Rule> existing) {
        if (candidate.minAge() == null && candidate.maxAge() != null) {
            throw new InvalidRequestException("INVALID_CATEGORY_RANGE", "maxAge requires minAge");
        }
        if (candidate.minAge() != null && candidate.maxAge() != null
                && candidate.maxAge() < candidate.minAge()) {
            throw new InvalidRequestException(
                    "INVALID_CATEGORY_RANGE",
                    "maxAge must be greater than or equal to minAge"
            );
        }
        if (!candidate.enabled() || candidate.minAge() == null) {
            return;
        }
        boolean overlaps = existing.stream()
                .filter(Rule::enabled)
                .filter(rule -> rule.minAge() != null)
                .filter(rule -> candidate.id() == null || !candidate.id().equals(rule.id()))
                .filter(rule -> gendersOverlap(rule.gender(), candidate.gender()))
                .anyMatch(rule -> rangesOverlap(rule, candidate));
        if (overlaps) {
            throw new RequestConflictException(
                    "CATEGORY_RANGE_OVERLAP",
                    "Enabled age categories must not overlap for the same applicable gender"
            );
        }
    }

    private static boolean gendersOverlap(CategoryGender left, CategoryGender right) {
        return left == null || right == null || left == right;
    }

    private static boolean rangesOverlap(Rule left, Rule right) {
        int leftMaximum = left.maxAge() == null ? Integer.MAX_VALUE : left.maxAge();
        int rightMaximum = right.maxAge() == null ? Integer.MAX_VALUE : right.maxAge();
        return left.minAge() <= rightMaximum && right.minAge() <= leftMaximum;
    }

    record Rule(
            Long id,
            Integer minAge,
            Integer maxAge,
            CategoryGender gender,
            boolean enabled
    ) {
    }
}
