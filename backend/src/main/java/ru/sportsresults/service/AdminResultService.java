package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.UpdateRegistrationRequest;
import ru.sportsresults.api.dto.UpdateResultRequest;
import ru.sportsresults.domain.AdminChangeLog;
import ru.sportsresults.domain.AuditEntityType;
import ru.sportsresults.domain.Registration;
import ru.sportsresults.domain.Result;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.RegistrationRepository;
import ru.sportsresults.repository.ResultRepository;
import ru.sportsresults.repository.StartClusterRepository;
import ru.sportsresults.domain.StartCluster;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

@Service
public class AdminResultService {

    private final RegistrationRepository registrationRepository;
    private final ResultRepository resultRepository;
    private final AdminChangeLogRepository changeLogRepository;
    private final AgeCategoryRecalculationService categoryRecalculationService;
    private final StartClusterRepository startClusterRepository;
    private final EventResultDataMutationGuard mutationGuard;

    public AdminResultService(
            RegistrationRepository registrationRepository,
            ResultRepository resultRepository,
            AdminChangeLogRepository changeLogRepository,
            AgeCategoryRecalculationService categoryRecalculationService,
            StartClusterRepository startClusterRepository,
            EventResultDataMutationGuard mutationGuard
    ) {
        this.registrationRepository = registrationRepository;
        this.resultRepository = resultRepository;
        this.changeLogRepository = changeLogRepository;
        this.categoryRecalculationService = categoryRecalculationService;
        this.startClusterRepository = startClusterRepository;
        this.mutationGuard = mutationGuard;
    }

    @Transactional
    public void updateRegistration(Long registrationId, UpdateRegistrationRequest request, String actor) {
        Long eventId = registrationRepository.findEventIdByRegistrationId(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REGISTRATION_NOT_FOUND", "Registration not found"));
        mutationGuard.lock(eventId);
        Registration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "REGISTRATION_NOT_FOUND",
                        "Registration not found"
                ));
        rejectRetired(registration);
        if (request.birthDate() != null && request.birthDate().isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new InvalidRequestException("INVALID_BIRTH_DATE", "birthDate cannot be in the future");
        }
        List<AdminChangeLog> changes = new ArrayList<>();

        change(changes, actor, AuditEntityType.REGISTRATION, registrationId, "displayName",
                registration.getDisplayName(), request.displayName().strip(), registration::setDisplayName);
        change(changes, actor, AuditEntityType.REGISTRATION, registrationId, "firstName",
                registration.getFirstName(), normalize(request.firstName()), registration::setFirstName);
        change(changes, actor, AuditEntityType.REGISTRATION, registrationId, "lastName",
                registration.getLastName(), normalize(request.lastName()), registration::setLastName);
        change(changes, actor, AuditEntityType.REGISTRATION, registrationId, "birthDate",
                registration.getBirthDate(), request.birthDate(), registration::setBirthDate);
        change(changes, actor, AuditEntityType.REGISTRATION, registrationId, "gender",
                registration.getGender(), normalize(request.gender()), registration::setGender);
        change(changes, actor, AuditEntityType.REGISTRATION, registrationId, "bib",
                registration.getBib(), normalize(request.bib()), registration::setBib);
        change(changes, actor, AuditEntityType.REGISTRATION, registrationId, "sourceCategory",
                registration.getSourceCategory(), normalize(request.sourceCategory()), registration::setSourceCategory);
        StartCluster cluster = null;
        if (request.clusterId() != null) {
            cluster = startClusterRepository.findByIdAndRaceId(request.clusterId(), registration.getRace().getId())
                    .orElseThrow(() -> new InvalidRequestException(
                            "CLUSTER_RACE_MISMATCH", "cluster must belong to the registration race"));
        }
        Long oldClusterId = registration.getCluster() == null ? null : registration.getCluster().getId();
        if (!Objects.equals(oldClusterId, request.clusterId())) {
            changes.add(log(actor, AuditEntityType.REGISTRATION, registrationId, "clusterId", oldClusterId, request.clusterId()));
            registration.setCluster(cluster);
        }
        change(changes, actor, AuditEntityType.REGISTRATION, registrationId, "entryKind",
                registration.getEntryKind(), request.entryKind(), registration::setEntryKind);

        Long oldCategoryId = registration.getCategory() == null ? null : registration.getCategory().getId();
        categoryRecalculationService.recalculateRegistration(registration);
        Long newCategoryId = registration.getCategory() == null ? null : registration.getCategory().getId();
        if (!Objects.equals(oldCategoryId, newCategoryId)) {
            changes.add(log(actor, AuditEntityType.REGISTRATION, registrationId,
                    "categoryId", oldCategoryId, newCategoryId));
        }
        if (!changes.isEmpty()) {
            registrationRepository.flush();
            changeLogRepository.saveAll(changes);
            mutationGuard.bump(registration.getRace().getEvent());
        }
    }

    @Transactional
    public void updateResult(Long resultId, UpdateResultRequest request, String actor) {
        Long eventId = resultRepository.findEventIdByResultId(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("RESULT_NOT_FOUND", "Result not found"));
        mutationGuard.lock(eventId);
        Result result = resultRepository.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("RESULT_NOT_FOUND", "Result not found"));
        rejectRetired(result.getRegistration());
        List<AdminChangeLog> changes = new ArrayList<>();

        change(changes, actor, AuditEntityType.RESULT, resultId, "status",
                result.getStatus(), request.status().strip(), result::setStatus);
        change(changes, actor, AuditEntityType.RESULT, resultId, "gunTimeMs",
                milliseconds(result.getGunTime()), request.gunTimeMs(),
                value -> result.setGunTime(duration(value)));
        change(changes, actor, AuditEntityType.RESULT, resultId, "chipTimeMs",
                milliseconds(result.getChipTime()), request.chipTimeMs(),
                value -> result.setChipTime(duration(value)));
        change(changes, actor, AuditEntityType.RESULT, resultId, "overallPlace",
                result.getOverallPlace(), request.overallPlace(), result::setOverallPlace);
        change(changes, actor, AuditEntityType.RESULT, resultId, "genderPlace",
                result.getGenderPlace(), request.genderPlace(), result::setGenderPlace);
        change(changes, actor, AuditEntityType.RESULT, resultId, "categoryPlace",
                result.getCategoryPlace(), request.categoryPlace(), result::setCategoryPlace);
        change(changes, actor, AuditEntityType.RESULT, resultId, "netOverallPlace",
                result.getNetOverallPlace(), request.netOverallPlace(), result::setNetOverallPlace);
        change(changes, actor, AuditEntityType.RESULT, resultId, "netGenderPlace",
                result.getNetGenderPlace(), request.netGenderPlace(), result::setNetGenderPlace);
        change(changes, actor, AuditEntityType.RESULT, resultId, "netCategoryPlace",
                result.getNetCategoryPlace(), request.netCategoryPlace(), result::setNetCategoryPlace);

        if (!changes.isEmpty()) {
            resultRepository.saveAndFlush(result);
            changeLogRepository.saveAll(changes);
            mutationGuard.bump(result.getRegistration().getRace().getEvent());
        }
    }

    private static <T> void change(
            List<AdminChangeLog> changes,
            String actor,
            AuditEntityType entityType,
            Long entityId,
            String field,
            T oldValue,
            T newValue,
            Consumer<T> setter
    ) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        setter.accept(newValue);
        changes.add(log(actor, entityType, entityId, field, oldValue, newValue));
    }

    private static void rejectRetired(Registration registration) {
        if (!registration.isCurrent()) {
            throw new RequestConflictException(
                    "RETIRED_REGISTRATION_NOT_MUTABLE",
                    "Retired registration is historical and cannot be changed by the current-data endpoint"
            );
        }
    }

    private static AdminChangeLog log(
            String actor,
            AuditEntityType entityType,
            Long entityId,
            String field,
            Object oldValue,
            Object newValue
    ) {
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setFieldName(field);
        log.setOldValue(stringValue(oldValue));
        log.setNewValue(stringValue(newValue));
        return log;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Long milliseconds(Duration duration) {
        return duration == null ? null : duration.toMillis();
    }

    private static Duration duration(Long milliseconds) {
        return milliseconds == null ? null : Duration.ofMillis(milliseconds);
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }
}
