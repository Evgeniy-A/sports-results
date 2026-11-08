package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.Race;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.RaceRepository;
import ru.sportsresults.repository.RegistrationRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

@Service
public class RegistrationFactualMutationService {

    private final RegistrationRepository registrationRepository;
    private final RaceRepository raceRepository;
    private final AgeCategoryRecalculationService categoryRecalculationService;
    private final AdminChangeLogRepository changeLogRepository;

    public RegistrationFactualMutationService(
            RegistrationRepository registrationRepository,
            RaceRepository raceRepository,
            AgeCategoryRecalculationService categoryRecalculationService,
            AdminChangeLogRepository changeLogRepository
    ) {
        this.registrationRepository = registrationRepository;
        this.raceRepository = raceRepository;
        this.categoryRecalculationService = categoryRecalculationService;
        this.changeLogRepository = changeLogRepository;
    }

    public DesiredRegistration validate(
            Registration registration,
            String displayName,
            String gender,
            LocalDate birthDate,
            String bib,
            Long raceId
    ) {
        String normalizedName = normalizeRequired(displayName, "desiredDisplayName", 320);
        String normalizedGender = normalizeGender(gender);
        String normalizedBib = normalizeNullable(bib, "desiredBib", 64);
        if (birthDate != null && birthDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new InvalidRequestException("INVALID_BIRTH_DATE", "desiredBirthDate cannot be in the future");
        }
        if (raceId == null) {
            throw new InvalidRequestException("JOURNAL_RACE_REQUIRED", "desiredRaceId is required");
        }
        Long eventId = registration.getRace().getEvent().getId();
        Race race = raceRepository.findByIdAndEventId(raceId, eventId)
                .orElseThrow(() -> new InvalidRequestException(
                        "JOURNAL_RACE_EVENT_MISMATCH", "desired race must belong to the same event"));
        return new DesiredRegistration(normalizedName, normalizedGender, birthDate, normalizedBib, race);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(Registration registration, DesiredRegistration desired, String actor) {
        List<AdminChangeLog> changes = new ArrayList<>();
        Long registrationId = registration.getId();
        Long oldRaceId = registration.getRace().getId();
        Long newRaceId = desired.race().getId();
        Long oldCategoryId = registration.getCategory() == null ? null : registration.getCategory().getId();
        boolean recalculateCategory = !Objects.equals(registration.getGender(), desired.gender())
                || !Objects.equals(registration.getBirthDate(), desired.birthDate())
                || !Objects.equals(oldRaceId, newRaceId);

        change(changes, actor, registrationId, "displayName",
                registration.getDisplayName(), desired.displayName(), registration::setDisplayName);
        change(changes, actor, registrationId, "gender",
                registration.getGender(), desired.gender(), registration::setGender);
        change(changes, actor, registrationId, "birthDate",
                registration.getBirthDate(), desired.birthDate(), registration::setBirthDate);
        change(changes, actor, registrationId, "bib",
                registration.getBib(), desired.bib(), registration::setBib);
        if (!Objects.equals(oldRaceId, newRaceId)) {
            changes.add(log(actor, registrationId, "raceId", oldRaceId, newRaceId));
            if (registration.getCluster() != null) {
                changes.add(log(actor, registrationId, "clusterId", registration.getCluster().getId(), null));
                registration.setCluster(null);
            }
            registration.setRace(desired.race());
        }
        if (recalculateCategory) {
            categoryRecalculationService.recalculateRegistration(registration);
            Long newCategoryId = registration.getCategory() == null ? null : registration.getCategory().getId();
            if (!Objects.equals(oldCategoryId, newCategoryId)) {
                changes.add(log(actor, registrationId, "categoryId", oldCategoryId, newCategoryId));
            }
        }
        if (changes.isEmpty()) {
            return false;
        }
        registrationRepository.saveAndFlush(registration);
        changeLogRepository.saveAll(changes);
        return true;
    }

    private static String normalizeRequired(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("JOURNAL_DISPLAY_NAME_REQUIRED", field + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new InvalidRequestException("JOURNAL_FIELD_TOO_LONG", field + " is too long");
        }
        return normalized;
    }

    private static String normalizeNullable(String value, String field, int maxLength) {
        String normalized = value == null || value.isBlank() ? null : value.strip();
        if (normalized != null && normalized.length() > maxLength) {
            throw new InvalidRequestException("JOURNAL_FIELD_TOO_LONG", field + " is too long");
        }
        return normalized;
    }

    private static String normalizeGender(String value) {
        String normalized = value == null || value.isBlank()
                ? null : value.strip().toLowerCase(Locale.ROOT);
        if (normalized != null && !normalized.equals("male") && !normalized.equals("female")) {
            throw new InvalidRequestException(
                    "INVALID_GENDER", "desiredGender must be male, female or null");
        }
        return normalized;
    }

    private static <T> void change(
            List<AdminChangeLog> changes,
            String actor,
            Long registrationId,
            String field,
            T oldValue,
            T newValue,
            Consumer<T> setter
    ) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        setter.accept(newValue);
        changes.add(log(actor, registrationId, field, oldValue, newValue));
    }

    private static AdminChangeLog log(
            String actor,
            Long registrationId,
            String field,
            Object oldValue,
            Object newValue
    ) {
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.REGISTRATION);
        log.setEntityId(registrationId);
        log.setFieldName(field);
        log.setOldValue(stringValue(oldValue));
        log.setNewValue(stringValue(newValue));
        return log;
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public record DesiredRegistration(
            String displayName,
            String gender,
            LocalDate birthDate,
            String bib,
            Race race
    ) {
    }
}
