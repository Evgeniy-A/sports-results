package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.AgeCalculationMode;
import ru.sportsresults.domain.AwardPolicy;
import ru.sportsresults.domain.Category;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.repository.AwardPolicyRepository;
import ru.sportsresults.repository.CategoryAssignmentRepository;
import ru.sportsresults.repository.CategoryRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RegistrationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class AgeCategoryRecalculationService {

    private final AgeCategoryResolver resolver;
    private final RaceRepository raceRepository;
    private final CategoryRepository categoryRepository;
    private final AwardPolicyRepository awardPolicyRepository;
    private final RegistrationRepository registrationRepository;
    private final CategoryAssignmentRepository assignmentRepository;

    public AgeCategoryRecalculationService(
            AgeCategoryResolver resolver,
            RaceRepository raceRepository,
            CategoryRepository categoryRepository,
            AwardPolicyRepository awardPolicyRepository,
            RegistrationRepository registrationRepository,
            CategoryAssignmentRepository assignmentRepository
    ) {
        this.resolver = resolver;
        this.raceRepository = raceRepository;
        this.categoryRepository = categoryRepository;
        this.awardPolicyRepository = awardPolicyRepository;
        this.registrationRepository = registrationRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Transactional
    public int recalculateRace(Long raceId) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found"));
        List<Category> categories = categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(raceId);
        AgeCalculationMode mode = awardPolicyRepository.findByRaceId(raceId)
                .map(AwardPolicy::getAgeCalculationMode)
                .orElse(AgeCalculationMode.EVENT_DATE);
        List<CategoryAssignmentRepository.CategoryAssignment> changes = new ArrayList<>();
        for (CategoryAssignmentRepository.RegistrationCategoryFacts facts
                : assignmentRepository.findAllByRaceId(raceId)) {
            Category resolved = resolver.resolve(
                    facts.birthDate(),
                    facts.gender(),
                    facts.sourceCategory(),
                    race.getEvent().getStartsAt(),
                    race.getEvent().getTimeZone(),
                    mode,
                    categories
            );
            Long categoryId = resolved == null ? null : resolved.getId();
            if (!Objects.equals(facts.currentCategoryId(), categoryId)) {
                changes.add(new CategoryAssignmentRepository.CategoryAssignment(
                        facts.registrationId(), categoryId
                ));
            }
        }
        assignmentRepository.updateAll(changes);
        return changes.size();
    }

    @Transactional
    public int recalculateEvent(Long eventId) {
        return raceRepository.findAllByEventIdOrderByDisplayOrderAsc(eventId).stream()
                .mapToInt(race -> recalculateRace(race.getId()))
                .sum();
    }

    @Transactional
    public Category recalculateRegistration(Registration registration) {
        Long raceId = registration.getRace().getId();
        List<Category> categories = categoryRepository.findAllByRaceIdOrderByDisplayOrderAsc(raceId);
        AgeCalculationMode mode = awardPolicyRepository.findByRaceId(raceId)
                .map(AwardPolicy::getAgeCalculationMode)
                .orElse(AgeCalculationMode.EVENT_DATE);
        Category category = resolver.resolve(
                registration.getBirthDate(),
                registration.getGender(),
                registration.getSourceCategory(),
                registration.getRace().getEvent().getStartsAt(),
                registration.getRace().getEvent().getTimeZone(),
                mode,
                categories
        );
        registration.setCategory(category);
        registrationRepository.save(registration);
        return category;
    }
}
