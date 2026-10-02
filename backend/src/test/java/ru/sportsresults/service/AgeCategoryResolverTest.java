package ru.sportsresults.service;

import org.junit.jupiter.api.Test;
import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.CategoryGender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgeCategoryResolverTest {

    private static final Instant EVENT_DATE = Instant.parse("2027-06-15T00:00:00Z");

    private final AgeCategoryResolver resolver = new AgeCategoryResolver();

    @Test
    void calculatesCompleteYearsForEventDateBeforeAndAfterBirthday() {
        Category ageThirtySix = category("36 Male", 36, 36, CategoryGender.MALE);
        Category ageThirtySeven = category("37 Male", 37, 37, CategoryGender.MALE);

        assertThat(resolve(LocalDate.of(1990, 9, 20), AgeCalculationMode.EVENT_DATE,
                List.of(ageThirtySix, ageThirtySeven))).isSameAs(ageThirtySix);
        assertThat(resolve(LocalDate.of(1990, 5, 1), AgeCalculationMode.EVENT_DATE,
                List.of(ageThirtySix, ageThirtySeven))).isSameAs(ageThirtySeven);
    }

    @Test
    void usesEndOfEventYearReferenceDate() {
        Category ageThirtySeven = category("37 Male", 37, 37, CategoryGender.MALE);

        assertThat(resolve(LocalDate.of(1990, 9, 20), AgeCalculationMode.END_OF_EVENT_YEAR,
                List.of(ageThirtySeven))).isSameAs(ageThirtySeven);
    }

    @Test
    void usesEventLocalDateInsteadOfUtcDate() {
        Instant shortlyBeforeUtcMidnight = Instant.parse("2027-06-14T21:30:00Z");
        Category thirtySix = category("36 Male", 36, 36, CategoryGender.MALE);
        Category thirtySeven = category("37 Male", 37, 37, CategoryGender.MALE);

        Category resolved = resolver.resolve(LocalDate.of(1990, 6, 15), "male", null,
                shortlyBeforeUtcMidnight, "Europe/Moscow", AgeCalculationMode.EVENT_DATE,
                List.of(thirtySix, thirtySeven));

        assertThat(resolved).isSameAs(thirtySeven);
        assertThat(AgeCategoryResolver.referenceDate(shortlyBeforeUtcMidnight, "Europe/Moscow",
                AgeCalculationMode.END_OF_EVENT_YEAR)).isEqualTo(LocalDate.of(2027, 12, 31));
    }

    @Test
    void honorsInclusiveBoundariesAndOpenUpperRange() {
        Category ageEighteenToTwentyNine = category("18-29 Male", 18, 29, CategoryGender.MALE);
        Category ageThirtyToThirtyNine = category("30-39 Male", 30, 39, CategoryGender.MALE);
        Category ageFiftyPlus = category("50+ Male", 50, null, CategoryGender.MALE);

        assertThat(resolve(LocalDate.of(1998, 6, 15), AgeCalculationMode.EVENT_DATE,
                List.of(ageEighteenToTwentyNine, ageThirtyToThirtyNine))).isSameAs(ageEighteenToTwentyNine);
        assertThat(resolve(LocalDate.of(1997, 6, 15), AgeCalculationMode.EVENT_DATE,
                List.of(ageEighteenToTwentyNine, ageThirtyToThirtyNine))).isSameAs(ageThirtyToThirtyNine);
        assertThat(resolve(LocalDate.of(1977, 6, 15), AgeCalculationMode.EVENT_DATE,
                List.of(ageFiftyPlus))).isSameAs(ageFiftyPlus);
        assertThat(resolve(LocalDate.of(1954, 6, 15), AgeCalculationMode.EVENT_DATE,
                List.of(ageFiftyPlus))).isSameAs(ageFiftyPlus);
    }

    @Test
    void usesExactSourceCategoryOnlyWhenBirthDateIsAbsent() {
        Category source = category("30-39 Male", 30, 39, CategoryGender.MALE);

        assertThat(resolver.resolve(
                null, "male", "30-39 Male", EVENT_DATE, "UTC",
                AgeCalculationMode.EVENT_DATE, List.of(source)
        )).isSameAs(source);
        assertThat(resolver.resolve(
                null, "male", "30M", EVENT_DATE, "UTC",
                AgeCalculationMode.EVENT_DATE, List.of(source)
        )).isNull();
    }

    @Test
    void neverAssignsDisabledCategoryForAdultMinorOrNoBirthDate() {
        Category disabledAdult = category("30-39 Male", 30, 39, CategoryGender.MALE);
        disabledAdult.setEnabled(false);
        Category disabledMinor = category("CHILD_17", null, null, null);
        disabledMinor.setEnabled(false);

        assertThat(resolver.resolve(
                LocalDate.of(1990, 1, 1), "male", "30-39 Male", EVENT_DATE, "UTC",
                AgeCalculationMode.EVENT_DATE, List.of(disabledAdult)
        )).isNull();
        assertThat(resolver.resolve(
                null, "male", "30-39 Male", EVENT_DATE, "UTC",
                AgeCalculationMode.EVENT_DATE, List.of(disabledAdult)
        )).isNull();
        assertThatThrownBy(() -> resolver.resolve(
                LocalDate.of(2015, 1, 1), "male", "CHILD_17", EVENT_DATE, "UTC",
                AgeCalculationMode.END_OF_EVENT_YEAR, List.of(disabledMinor)
        )).isInstanceOfSatisfying(InvalidRequestException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("MINOR_SOURCE_CATEGORY_NOT_CONFIGURED"));
    }

    @Test
    void birthDateWinsOverConflictingSourceAndDoesNotBypassConfiguredGap() {
        Category source = category("30-39 Male", 30, 39, CategoryGender.MALE);
        Category ageFortyToFortyNine = category("40-49 Male", 40, 49, CategoryGender.MALE);

        assertThat(resolver.resolve(
                LocalDate.of(1985, 1, 1), "male", "30-39 Male", EVENT_DATE, "UTC",
                AgeCalculationMode.EVENT_DATE, List.of(source, ageFortyToFortyNine)
        )).isSameAs(ageFortyToFortyNine);
        assertThat(resolver.resolve(
                LocalDate.of(1992, 1, 1), "male", "30-39 Male", EVENT_DATE, "UTC",
                AgeCalculationMode.EVENT_DATE,
                List.of(category("18-29 Male", 18, 29, CategoryGender.MALE), ageFortyToFortyNine)
        )).isNull();
    }

    @Test
    void doesNotGuessMissingGenderOrResolveWithoutFacts() {
        Category male = category("30-39 Male", 30, 39, CategoryGender.MALE);

        assertThat(resolver.resolve(
                LocalDate.of(1990, 1, 1), null, "30-39 Male", EVENT_DATE, "UTC",
                AgeCalculationMode.EVENT_DATE, List.of(male)
        )).isNull();
        assertThat(resolver.resolve(
                null, "male", null, EVENT_DATE, "UTC",
                AgeCalculationMode.EVENT_DATE, List.of(male)
        )).isNull();
    }

    @Test
    void rejectsBirthDateAfterReferenceDate() {
        assertThatThrownBy(() -> resolver.resolve(
                LocalDate.of(2028, 1, 1), "male", null, EVENT_DATE, "UTC",
                AgeCalculationMode.EVENT_DATE, List.of()
        )).isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("birthDate");
    }

    @Test
    void minorsUseExactSourceCategoryAndIgnoreAdultYearEndPolicy() {
        Category source = category("CHILD_17", null, null, null);
        Category adult = category("18-29 Male", 18, 29, CategoryGender.MALE);

        AgeCategoryResolution<Category> resolution = resolver.resolveDetailed(
                LocalDate.of(2009, 6, 16),
                "male",
                "CHILD_17",
                Instant.parse("2027-06-15T12:00:00Z"),
                "UTC",
                AgeCalculationMode.END_OF_EVENT_YEAR,
                List.of(source, adult)
        );

        assertThat(resolution.branch()).isEqualTo(AgeCategoryBranch.MINOR);
        assertThat(resolution.category()).isSameAs(source);
        assertThat(resolution.reason()).isEqualTo(AgeCategoryResolutionReason.MINOR_SOURCE_CATEGORY);
    }

    @Test
    void minorSourceIsAuthoritativeEvenWhenLabelLooksInconsistent() {
        Category source = category("14-15", null, null, null);

        assertThat(resolver.resolve(
                LocalDate.of(2015, 1, 1), "female", "14-15",
                EVENT_DATE, "UTC", AgeCalculationMode.EVENT_DATE, List.of(source)
        )).isSameAs(source);
    }

    @Test
    void exactlyEighteenUsesAdultCalculation() {
        Category adult = category("18-29 Male", 18, 29, CategoryGender.MALE);

        AgeCategoryResolution<Category> resolution = resolver.resolveDetailed(
                LocalDate.of(2009, 6, 15), "male", "CHILD",
                Instant.parse("2027-06-15T12:00:00Z"), "UTC",
                AgeCalculationMode.EVENT_DATE, List.of(adult)
        );

        assertThat(resolution.branch()).isEqualTo(AgeCategoryBranch.ADULT);
        assertThat(resolution.category()).isSameAs(adult);
    }

    @Test
    void blocksMinorWithoutSourceCategory() {
        assertThatThrownBy(() -> resolver.resolve(
                LocalDate.of(2015, 1, 1), "female", null,
                EVENT_DATE, "UTC", AgeCalculationMode.EVENT_DATE, List.of()
        )).isInstanceOfSatisfying(InvalidRequestException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("MINOR_SOURCE_CATEGORY_REQUIRED"));
    }

    @Test
    void adultWithoutConfiguredRangesStaysUnclassifiedDespiteSourceCategory() {
        Category provenance = category("Open", null, null, null);

        AgeCategoryResolution<Category> resolution = resolver.resolveDetailed(
                LocalDate.of(1990, 1, 1), "male", "Open",
                EVENT_DATE, "UTC", AgeCalculationMode.EVENT_DATE, List.of(provenance)
        );

        assertThat(resolution.branch()).isEqualTo(AgeCategoryBranch.ADULT);
        assertThat(resolution.category()).isNull();
        assertThat(resolution.reason())
                .isEqualTo(AgeCategoryResolutionReason.ADULT_NO_CATEGORY_CONFIGURATION);
    }

    private Category resolve(
            LocalDate birthDate,
            AgeCalculationMode mode,
            List<Category> categories
    ) {
        return resolver.resolve(birthDate, "male", null, EVENT_DATE, "UTC", mode, categories);
    }

    private static Category category(
            String sourceName,
            Integer minAge,
            Integer maxAge,
            CategoryGender gender
    ) {
        Category category = new Category();
        category.setSourceName(sourceName);
        category.setDisplayName(sourceName);
        category.setMinAge(minAge);
        category.setMaxAge(maxAge);
        category.setGender(gender);
        category.setEnabled(true);
        return category;
    }
}
