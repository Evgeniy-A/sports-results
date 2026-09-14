package ru.sportsresults.service;

import org.springframework.stereotype.Component;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.ResultsPublicationStatus;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RegistrationRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class ResultConfigurationChangeGuard {

    private final RegistrationRepository registrationRepository;
    private final RaceRepository raceRepository;

    public ResultConfigurationChangeGuard(
            RegistrationRepository registrationRepository,
            RaceRepository raceRepository
    ) {
        this.registrationRepository = registrationRepository;
        this.raceRepository = raceRepository;
    }

    public void requireDraft(List<Race> affectedRaces) {
        if (affectedRaces.stream().anyMatch(
                race -> race.getResultsPublicationStatus() != ResultsPublicationStatus.DRAFT
        )) {
            throw new RequestConflictException(
                    "RACE_RESULTS_MUST_BE_DRAFT",
                    "Every Race affected by a result configuration change must be DRAFT"
            );
        }
    }

    public List<Race> currentDataRaces(List<Race> races) {
        if (races.isEmpty()) {
            return List.of();
        }
        Set<Long> withCurrentData = new HashSet<>(registrationRepository.findRaceIdsWithCurrentRegistrations(
                races.stream().map(Race::getId).toList()
        ));
        return races.stream().filter(race -> withCurrentData.contains(race.getId())).toList();
    }

    public void markRecalculationRequiredForCurrentData(List<Race> affectedRaces) {
        if (affectedRaces.isEmpty()) {
            return;
        }
        List<Race> changed = currentDataRaces(affectedRaces).stream()
                .filter(race -> !race.isResultRecalculationRequired())
                .peek(race -> race.setResultRecalculationRequired(true))
                .toList();
        if (!changed.isEmpty()) {
            raceRepository.saveAll(changed);
        }
    }
}
