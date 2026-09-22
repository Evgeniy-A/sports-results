package ru.sportsresults.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.sportsresults.domain.ImportMappingProfile;
import ru.sportsresults.importing.ImportFileType;

import java.util.List;
import java.util.Optional;

public interface ImportMappingProfileRepository extends JpaRepository<ImportMappingProfile, Long> {
    List<ImportMappingProfile> findAllByOrderByNameAsc();
    List<ImportMappingProfile> findAllByFileTypeAndHeaderSignatureOrderByUpdatedAtDesc(
            ImportFileType fileType,
            String headerSignature
    );
    Optional<ImportMappingProfile> findByNameIgnoreCase(String name);
}
