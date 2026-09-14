package ru.sportsresults.service;

import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.CategoryGender;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;

final class AgeCategorySelection {

    private AgeCategorySelection() {
    }

    static <T> T resolve(
            LocalDate birthDate,
            String registrationGender,
            String sourceCategory,
            Instant eventStartsAt,
            String eventTimeZone,
            AgeCalculationMode ageCalculationMode,
            List<Candidate<T>> categories
    ) {
        AgeCategoryResolution<T> resolution = resolveDetailed(
                birthDate,
                registrationGender,
                sourceCategory,
                eventStartsAt,
                eventTimeZone,
                ageCalculationMode,
                categories
        );
        if (resolution.blocked()) {
            throw new InvalidRequestException(resolution.blockingCode(), resolution.blockingMessage());
        }
        return resolution.category();
    }

    static <T> AgeCategoryResolution<T> resolveDetailed(
            LocalDate birthDate,
            String registrationGender,
            String sourceCategory,
            Instant eventStartsAt,
            String eventTimeZone,
            AgeCalculationMode ageCalculationMode,
            List<Candidate<T>> categories
    ) {
        List<Candidate<T>> enabled = categories.stream().filter(Candidate::enabled).toList();
        if (birthDate == null) {
            if (sourceCategory == null || sourceCategory.isBlank()) {
                return resolved(null, AgeCategoryBranch.UNKNOWN, AgeCategoryResolutionReason.NO_BIRTH_DATE);
            }
            List<Candidate<T>> matches = enabled.stream()
                    .filter(category -> category.sourceName().equals(sourceCategory))
                    .toList();
            return resolved(
                    matches.size() == 1 ? matches.getFirst().value() : null,
                    AgeCategoryBranch.UNKNOWN,
                    AgeCategoryResolutionReason.NO_BIRTH_DATE
            );
        }

        LocalDate eventDate = eventDate(eventStartsAt, eventTimeZone);
        if (eventDate == null) {
            return blocked(
                    AgeCategoryBranch.UNKNOWN,
                    AgeCategoryResolutionReason.ADULT_RECALCULATED,
                    "EVENT_DATE_REQUIRED_FOR_CATEGORY",
                    "Event start date is required to resolve a category for a known birth date"
            );
        }
        if (birthDate.isAfter(eventDate)) {
            return blocked(
                    AgeCategoryBranch.UNKNOWN,
                    AgeCategoryResolutionReason.ADULT_RECALCULATED,
                    "INVALID_BIRTH_DATE",
                    "birthDate must not be after the Event date"
            );
        }

        int ageAtEventDate = Period.between(birthDate, eventDate).getYears();
        if (ageAtEventDate < 18) {
            if (sourceCategory == null || sourceCategory.isBlank()) {
                return blocked(
                        AgeCategoryBranch.MINOR,
                        AgeCategoryResolutionReason.MINOR_SOURCE_CATEGORY,
                        "MINOR_SOURCE_CATEGORY_REQUIRED",
                        "A source category is required for a participant under 18 on the Event date"
                );
            }
            List<Candidate<T>> sourceMatches = categories.stream()
                    .filter(category -> category.sourceName().equals(sourceCategory))
                    .toList();
            if (sourceMatches.size() != 1) {
                return blocked(
                        AgeCategoryBranch.MINOR,
                        AgeCategoryResolutionReason.MINOR_SOURCE_CATEGORY,
                        "MINOR_SOURCE_CATEGORY_NOT_CONFIGURED",
                        "The minor source category must resolve to exactly one Category in the Race"
                );
            }
            return resolved(
                    sourceMatches.getFirst().value(),
                    AgeCategoryBranch.MINOR,
                    AgeCategoryResolutionReason.MINOR_SOURCE_CATEGORY
            );
        }

        List<Candidate<T>> configured = enabled.stream()
                .filter(category -> category.minAge() != null)
                .toList();
        if (configured.isEmpty()) {
            return resolved(
                    null,
                    AgeCategoryBranch.ADULT,
                    AgeCategoryResolutionReason.ADULT_NO_CATEGORY_CONFIGURATION
            );
        }
        LocalDate referenceDate = referenceDate(eventStartsAt, eventTimeZone, ageCalculationMode);
        int configuredAge = Period.between(birthDate, referenceDate).getYears();
        List<Candidate<T>> matches = configured.stream()
                .filter(category -> configuredAge >= category.minAge())
                .filter(category -> category.maxAge() == null || configuredAge <= category.maxAge())
                .filter(category -> matchesGender(category.gender(), registrationGender))
                .toList();
        if (matches.size() > 1) {
            return blocked(
                    AgeCategoryBranch.ADULT,
                    AgeCategoryResolutionReason.ADULT_RECALCULATED,
                    "CATEGORY_RANGE_AMBIGUOUS",
                    "More than one enabled age category matches the participant"
            );
        }
        return resolved(
                matches.isEmpty() ? null : matches.getFirst().value(),
                AgeCategoryBranch.ADULT,
                AgeCategoryResolutionReason.ADULT_RECALCULATED
        );
    }

    static LocalDate referenceDate(Instant eventStartsAt, String eventTimeZone, AgeCalculationMode mode) {
        LocalDate eventDate = eventDate(eventStartsAt, eventTimeZone);
        if (eventDate == null) return null;
        return switch (mode) {
            case EVENT_DATE -> eventDate;
            case END_OF_EVENT_YEAR -> LocalDate.of(eventDate.getYear(), 12, 31);
        };
    }

    private static LocalDate eventDate(Instant eventStartsAt, String eventTimeZone) {
        return eventStartsAt == null ? null : eventStartsAt.atZone(ZoneId.of(eventTimeZone)).toLocalDate();
    }

    private static <T> AgeCategoryResolution<T> resolved(
            T value,
            AgeCategoryBranch branch,
            AgeCategoryResolutionReason reason
    ) {
        return new AgeCategoryResolution<>(value, branch, reason, null, null);
    }

    private static <T> AgeCategoryResolution<T> blocked(
            AgeCategoryBranch branch,
            AgeCategoryResolutionReason reason,
            String code,
            String message
    ) {
        return new AgeCategoryResolution<>(null, branch, reason, code, message);
    }

    private static boolean matchesGender(CategoryGender categoryGender, String value) {
        if (categoryGender == null) return true;
        CategoryGender normalized = null;
        if (value != null && value.equalsIgnoreCase("male")) normalized = CategoryGender.MALE;
        if (value != null && value.equalsIgnoreCase("female")) normalized = CategoryGender.FEMALE;
        return categoryGender == normalized;
    }

    record Candidate<T>(
            T value,
            String sourceName,
            Integer minAge,
            Integer maxAge,
            CategoryGender gender,
            boolean enabled
    ) {
    }
}
