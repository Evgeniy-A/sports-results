package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.ResultIssueJournalShareSession;

import java.util.Optional;
import java.util.UUID;

public interface ResultIssueJournalShareSessionRepository
        extends JpaRepository<ResultIssueJournalShareSession, UUID> {

    @EntityGraph(attributePaths = {"grant", "grant.eventSeries"})
    Optional<ResultIssueJournalShareSession> findBySessionTokenHash(String sessionTokenHash);
}
