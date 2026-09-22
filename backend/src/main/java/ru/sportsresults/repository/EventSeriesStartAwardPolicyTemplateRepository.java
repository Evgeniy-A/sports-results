package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.EventSeriesStartAwardPolicyTemplate;

import java.util.List;
import java.util.Optional;

public interface EventSeriesStartAwardPolicyTemplateRepository
        extends JpaRepository<EventSeriesStartAwardPolicyTemplate, Long> {
    Optional<EventSeriesStartAwardPolicyTemplate> findByTemplateStartId(Long templateStartId);

    List<EventSeriesStartAwardPolicyTemplate> findAllByTemplateStartEventSeriesId(Long eventSeriesId);

    void deleteByTemplateStartId(Long templateStartId);
}
