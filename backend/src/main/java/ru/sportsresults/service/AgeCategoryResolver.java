package ru.sportsresults.service;

import org.springframework.stereotype.Component;
import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.Category;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
public class AgeCategoryResolver {

    public Category resolve(
            LocalDate birthDate,
            String registrationGender,
            String sourceCategory,
            Instant eventStartsAt,
            String eventTimeZone,
            AgeCalculationMode ageCalculationMode,
            List<Category> categories
    ) {
        return AgeCategorySelection.resolve(
                birthDate,
                registrationGender,
                sourceCategory,
                eventStartsAt,
                eventTimeZone,
                ageCalculationMode,
                categories.stream().map(category -> new AgeCategorySelection.Candidate<>(
                        category,
                        category.getSourceName(),
                        category.getMinAge(),
                        category.getMaxAge(),
                        category.getGender(),
                        category.isEnabled()
                )).toList()
        );
    }

    public AgeCategoryResolution<Category> resolveDetailed(
            LocalDate birthDate,
            String registrationGender,
            String sourceCategory,
            Instant eventStartsAt,
            String eventTimeZone,
            AgeCalculationMode ageCalculationMode,
            List<Category> categories
    ) {
        return AgeCategorySelection.resolveDetailed(
                birthDate,
                registrationGender,
                sourceCategory,
                eventStartsAt,
                eventTimeZone,
                ageCalculationMode,
                candidates(categories)
        );
    }

    private static List<AgeCategorySelection.Candidate<Category>> candidates(List<Category> categories) {
        return categories.stream().map(category -> new AgeCategorySelection.Candidate<>(
                category,
                category.getSourceName(),
                category.getMinAge(),
                category.getMaxAge(),
                category.getGender(),
                category.isEnabled()
        )).toList();
    }

    static LocalDate referenceDate(Instant eventStartsAt, String eventTimeZone, AgeCalculationMode mode) {
        return AgeCategorySelection.referenceDate(eventStartsAt, eventTimeZone, mode);
    }
}
