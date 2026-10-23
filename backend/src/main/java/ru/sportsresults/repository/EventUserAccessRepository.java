package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.sportsresults.domain.EventUserAccess;

import java.util.Collection;
import java.util.List;

public interface EventUserAccessRepository extends JpaRepository<EventUserAccess, Long> {
    boolean existsByUserUsernameIgnoreCaseAndEventId(String username, Long eventId);

    @Query("select access.event.id from EventUserAccess access where access.user.id = :userId order by access.event.id")
    List<Long> findEventIdsByUserId(@Param("userId") Long userId);

    @Query("select access.user.id, count(access) from EventUserAccess access "
            + "where access.user.id in :userIds group by access.user.id")
    List<Object[]> countByUserIds(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Query("delete from EventUserAccess access where access.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
