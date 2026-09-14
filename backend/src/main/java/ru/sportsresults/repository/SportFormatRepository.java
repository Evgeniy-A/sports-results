package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.SportFormat;

import java.util.List;
import java.util.Optional;

public interface SportFormatRepository extends JpaRepository<SportFormat, Long> {
    List<SportFormat> findAllByEventIdOrderByDisplayOrderAscIdAsc(Long eventId);

    Optional<SportFormat> findByIdAndEventId(Long id, Long eventId);

    Optional<SportFormat> findByEventIdAndCode(Long eventId, String code);

    Optional<SportFormat> findByEventIdAndSourceName(Long eventId, String sourceName);
}
