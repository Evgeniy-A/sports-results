package ru.sportsresults.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.ImportMappingProfileDto;
import ru.sportsresults.api.dto.UpsertImportMappingProfileRequest;
import ru.sportsresults.domain.ImportMappingProfile;
import ru.sportsresults.importing.CanonicalImportField;
import ru.sportsresults.importing.ImportFileType;
import ru.sportsresults.repository.ImportMappingProfileRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class ImportMappingProfileService {

    private final ImportMappingProfileRepository repository;
    private final ObjectMapper objectMapper;

    public ImportMappingProfileService(ImportMappingProfileRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ImportMappingProfileDto> list() {
        return repository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ImportMappingProfileDto exactMatch(ImportFileType fileType, String signature) {
        return repository.findAllByFileTypeAndHeaderSignatureOrderByUpdatedAtDesc(fileType, signature)
                .stream().findFirst().map(this::toDto).orElse(null);
    }

    @Transactional
    public ImportMappingProfileDto create(UpsertImportMappingProfileRequest request) {
        if (repository.findByNameIgnoreCase(request.name().strip()).isPresent()) {
            throw new RequestConflictException("IMPORT_MAPPING_PROFILE_EXISTS", "A mapping profile with this name already exists");
        }
        ImportMappingProfile profile = new ImportMappingProfile();
        apply(profile, request);
        return save(profile);
    }

    @Transactional
    public ImportMappingProfileDto update(Long profileId, UpsertImportMappingProfileRequest request) {
        ImportMappingProfile profile = repository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "IMPORT_MAPPING_PROFILE_NOT_FOUND", "Import mapping profile not found"
                ));
        repository.findByNameIgnoreCase(request.name().strip())
                .filter(other -> !other.getId().equals(profileId))
                .ifPresent(other -> { throw new RequestConflictException(
                        "IMPORT_MAPPING_PROFILE_EXISTS", "A mapping profile with this name already exists"
                ); });
        apply(profile, request);
        return save(profile);
    }

    @Transactional
    public void delete(Long profileId) {
        if (!repository.existsById(profileId)) {
            throw new ResourceNotFoundException("IMPORT_MAPPING_PROFILE_NOT_FOUND", "Import mapping profile not found");
        }
        repository.deleteById(profileId);
    }

    private void apply(ImportMappingProfile profile, UpsertImportMappingProfileRequest request) {
        validateMappings(request.mappings());
        profile.setName(request.name().strip());
        profile.setFileType(request.fileType());
        profile.setHeaderSignature(request.headerSignature());
        profile.setMappingJson(writeMappings(request.mappings()));
        profile.setRaceDiscriminatorHeader(blankToNull(request.raceDiscriminatorHeader()));
    }

    private ImportMappingProfileDto save(ImportMappingProfile profile) {
        try {
            return toDto(repository.saveAndFlush(profile));
        } catch (DataIntegrityViolationException exception) {
            throw new RequestConflictException("IMPORT_MAPPING_PROFILE_EXISTS", "Mapping profile conflicts with existing data");
        }
    }

    private ImportMappingProfileDto toDto(ImportMappingProfile profile) {
        return new ImportMappingProfileDto(
                profile.getId(), profile.getName(), profile.getFileType(), profile.getHeaderSignature(),
                readMappings(profile.getMappingJson()), profile.getRaceDiscriminatorHeader(),
                profile.getCreatedAt(), profile.getUpdatedAt()
        );
    }

    private String writeMappings(Map<String, CanonicalImportField> mappings) {
        try {
            return objectMapper.writeValueAsString(mappings);
        } catch (Exception exception) {
            throw new InvalidRequestException("INVALID_COLUMN_MAPPING", "Column mapping could not be serialized");
        }
    }

    private Map<String, CanonicalImportField> readMappings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, CanonicalImportField>>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Stored import mapping profile is invalid", exception);
        }
    }

    private static void validateMappings(Map<String, CanonicalImportField> mappings) {
        long unique = mappings.values().stream().distinct().count();
        if (unique != mappings.size()) {
            throw new InvalidRequestException(
                    "DUPLICATE_CANONICAL_MAPPING", "One Sports Results field cannot be mapped from multiple columns"
            );
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
