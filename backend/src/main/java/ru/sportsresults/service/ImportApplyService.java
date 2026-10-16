package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.api.dto.ImportApplyResponseDto;
import ru.sportsresults.api.dto.ManualConflictResolutionDto;
import ru.sportsresults.importing.ImportInputConfig;
import ru.sportsresults.repository.ImportOperationRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Service
public class ImportApplyService {

    private final ImportApplyFilePreparationService filePreparationService;
    private final ImportApplyTransactionService transactionService;
    private final ImportOperationRepository operationRepository;
    private final FlexibleImportFileService flexibleImportFileService;
    private final ObjectMapper objectMapper;

    public ImportApplyService(
            ImportApplyFilePreparationService filePreparationService,
            ImportApplyTransactionService transactionService,
            ImportOperationRepository operationRepository,
            FlexibleImportFileService flexibleImportFileService,
            ObjectMapper objectMapper
    ) {
        this.filePreparationService = filePreparationService;
        this.transactionService = transactionService;
        this.operationRepository = operationRepository;
        this.flexibleImportFileService = flexibleImportFileService;
        this.objectMapper = objectMapper;
    }

    public ImportApplyResponseDto apply(
            Long eventId,
            UUID operationId,
            byte[] contents,
            String actor
    ) {
        return apply(eventId, operationId, contents, "[]", actor);
    }

    public ImportApplyResponseDto apply(
            Long eventId,
            UUID operationId,
            byte[] contents,
            String manualConflictResolutions,
            String actor
    ) {
        if (actor == null || actor.isBlank()) {
            throw new InvalidRequestException("IMPORT_ACTOR_REQUIRED", "Authenticated import actor is required");
        }
        // Multipart reading, hashing and CSV parsing deliberately happen before the Event write lock.
        String fileSha256 = filePreparationService.fileSha256(contents);
        var previewMetadata = operationRepository.findWithEventById(operationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "IMPORT_OPERATION_NOT_FOUND", "Import operation not found"));
        if (!previewMetadata.getEvent().getId().equals(eventId)) {
            throw new InvalidRequestException(
                    "IMPORT_OPERATION_EVENT_MISMATCH", "Import operation belongs to another event");
        }
        if (!previewMetadata.getFileSha256().equals(fileSha256)) {
            throw new RequestConflictException("FILE_MISMATCH", "Uploaded file differs from the previewed file");
        }
        ImportInputConfig inputConfig = flexibleImportFileService.deserializeConfig(previewMetadata.getInputConfig());
        PreparedImportFile prepared = filePreparationService.prepare(
                eventId,
                previewMetadata.getSourceFilename(),
                contents,
                fileSha256,
                inputConfig
        );
        return transactionService.apply(
                eventId,
                operationId,
                prepared,
                parseManualConflictResolutions(manualConflictResolutions),
                actor.strip()
        );
    }

    private List<ManualConflictResolutionDto> parseManualConflictResolutions(String json) {
        String value = json == null || json.isBlank() ? "[]" : json;
        if (value.length() > 1_000_000) {
            throw new InvalidRequestException(
                    "INVALID_MANUAL_CONFLICT_RESOLUTIONS",
                    "Manual conflict resolutions payload is too large"
            );
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<ManualConflictResolutionDto>>() {});
        } catch (Exception exception) {
            throw new InvalidRequestException(
                    "INVALID_MANUAL_CONFLICT_RESOLUTIONS",
                    "Manual conflict resolutions must be a valid JSON array"
            );
        }
    }
}
