package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.CertificateTemplate;

import java.util.List;
import java.util.Optional;

public interface CertificateTemplateRepository extends JpaRepository<CertificateTemplate, Long> {
    List<CertificateTemplate> findAllByEventIdOrderByDisplayOrderAscIdAsc(Long eventId);
    Optional<CertificateTemplate> findByIdAndEventId(Long id, Long eventId);
}
