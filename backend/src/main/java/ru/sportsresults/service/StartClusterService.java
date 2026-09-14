package ru.sportsresults.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.StartClusterDto;
import ru.sportsresults.api.dto.UpsertStartClusterRequest;
import ru.sportsresults.domain.*;
import ru.sportsresults.repository.*;

import java.util.List;

@Service
public class StartClusterService {
    private final RaceRepository raceRepository;
    private final StartClusterRepository clusterRepository;
    private final RegistrationRepository registrationRepository;
    private final AdminChangeLogRepository auditRepository;
    private final EventResultDataMutationGuard mutationGuard;

    public StartClusterService(RaceRepository raceRepository,
                               StartClusterRepository clusterRepository, RegistrationRepository registrationRepository,
                               AdminChangeLogRepository auditRepository,
                               EventResultDataMutationGuard mutationGuard) {
        this.raceRepository = raceRepository;
        this.clusterRepository = clusterRepository;
        this.registrationRepository = registrationRepository;
        this.auditRepository = auditRepository;
        this.mutationGuard = mutationGuard;
    }

    @Transactional(readOnly = true)
    public List<StartClusterDto> list(Long eventId, Long raceId) {
        requireRace(eventId, raceId);
        return clusterRepository.findAllByRaceIdOrderByDisplayOrderAscIdAsc(raceId).stream()
                .map(StartClusterService::toDto).toList();
    }

    @Transactional
    public StartClusterDto create(Long eventId, Long raceId, UpsertStartClusterRequest request, String actor) {
        Event event = mutationGuard.lock(eventId);
        StartCluster cluster = new StartCluster();
        cluster.setRace(requireRace(eventId, raceId));
        apply(cluster, request);
        try {
            StartCluster saved = clusterRepository.saveAndFlush(cluster);
            audit(actor, saved.getId(), "created", null, toDto(saved).toString());
            mutationGuard.bump(event);
            return toDto(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("START_CLUSTER_EXISTS", "Cluster code, source name, or display name already exists in this race");
        }
    }

    @Transactional
    public StartClusterDto update(Long eventId, Long raceId, Long clusterId,
                                  UpsertStartClusterRequest request, String actor) {
        Event event = mutationGuard.lock(eventId);
        requireRace(eventId, raceId);
        StartCluster cluster = clusterRepository.findByIdAndRaceId(clusterId, raceId)
                .orElseThrow(() -> new ResourceNotFoundException("START_CLUSTER_NOT_FOUND", "Start cluster not found"));
        String old = toDto(cluster).toString();
        apply(cluster, request);
        try {
            StartCluster saved = clusterRepository.saveAndFlush(cluster);
            audit(actor, clusterId, "updated", old, toDto(saved).toString());
            if (!old.equals(toDto(saved).toString())) {
                mutationGuard.bump(event);
            }
            return toDto(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("START_CLUSTER_EXISTS", "Cluster code, source name, or display name already exists in this race");
        }
    }

    @Transactional
    public void delete(Long eventId, Long raceId, Long clusterId, String actor) {
        Event event = mutationGuard.lock(eventId);
        requireRace(eventId, raceId);
        StartCluster cluster = clusterRepository.findByIdAndRaceId(clusterId, raceId)
                .orElseThrow(() -> new ResourceNotFoundException("START_CLUSTER_NOT_FOUND", "Start cluster not found"));
        if (registrationRepository.existsByClusterId(clusterId)) {
            throw new RequestConflictException("START_CLUSTER_IN_USE", "A cluster assigned to registrations cannot be deleted");
        }
        clusterRepository.delete(cluster);
        audit(actor, clusterId, "deleted", toDto(cluster).toString(), null);
        mutationGuard.bump(event);
    }

    private Race requireRace(Long eventId, Long raceId) {
        return raceRepository.findByIdAndEventId(raceId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("RACE_NOT_FOUND", "Race not found in this event"));
    }

    private static void apply(StartCluster cluster, UpsertStartClusterRequest request) {
        cluster.setCode(normalize(request.code()));
        cluster.setSourceName(normalize(request.sourceName()));
        cluster.setDisplayName(request.displayName().strip());
        cluster.setDisplayOrder(request.displayOrder());
        cluster.setStartsAt(request.startsAt());
    }

    public static StartClusterDto toDto(StartCluster cluster) {
        return new StartClusterDto(cluster.getId(), cluster.getRace().getId(), cluster.getCode(), cluster.getSourceName(),
                cluster.getDisplayName(), cluster.getDisplayOrder(), cluster.getStartsAt());
    }

    private void audit(String actor, Long id, String field, String oldValue, String newValue) {
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.START_CLUSTER);
        log.setEntityId(id);
        log.setFieldName(field);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        auditRepository.save(log);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
