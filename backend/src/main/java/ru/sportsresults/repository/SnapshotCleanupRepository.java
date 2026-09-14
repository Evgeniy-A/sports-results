package ru.sportsresults.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

@Repository
public class SnapshotCleanupRepository {

    private final EntityManager entityManager;

    public SnapshotCleanupRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void deleteEventSnapshot(Long eventId) {
        entityManager.createNativeQuery("""
                DELETE FROM splits s
                USING results r, registrations reg, races ra
                WHERE s.result_id = r.id
                  AND r.registration_id = reg.id
                  AND reg.race_id = ra.id
                  AND ra.event_id = :eventId
                """).setParameter("eventId", eventId).executeUpdate();
        entityManager.createNativeQuery("""
                DELETE FROM results r
                USING registrations reg, races ra
                WHERE r.registration_id = reg.id
                  AND reg.race_id = ra.id
                  AND ra.event_id = :eventId
                """).setParameter("eventId", eventId).executeUpdate();
        entityManager.createNativeQuery("""
                DELETE FROM registrations reg
                USING races ra
                WHERE reg.race_id = ra.id
                  AND ra.event_id = :eventId
                """).setParameter("eventId", eventId).executeUpdate();
    }
}
