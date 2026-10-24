package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.CertificateSettings;

import java.util.Optional;

public interface CertificateSettingsRepository extends JpaRepository<CertificateSettings, Long> {
    Optional<CertificateSettings> findByEventId(Long eventId);
}
