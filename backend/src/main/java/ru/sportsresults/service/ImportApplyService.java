package ru.sportsresults.service;

import org.springframework.stereotype.Service;
import ru.sportsresults.api.dto.ImportApplyResponseDto;
import ru.sportsresults.repository.ImportOperationRepository;

import java.util.UUID;

@Service
public class ImportApplyService {

    private final ImportApplyFilePreparationService filePreparationService;
    private final ImportApplyTransactionService transactionService;
    private final ImportOperationRepository operationRepository;

    public ImportApplyService(
            ImportApplyFilePreparationService filePreparationService,
            ImportApplyTransactionService transactionService,
            ImportOperationRepository operationRepository
    ) {
        this.filePreparationService = filePreparationService;
        this.transactionService = transactionService;
        this.operationRepository = operationRepository;
    }

    public ImportApplyResponseDto apply(
            Long eventId,
            UUID operationId,
            byte[] contents,
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
        PreparedImportFile prepared = filePreparationService.prepare(contents, fileSha256);
        return transactionService.apply(eventId, operationId, prepared, actor.strip());
    }
}
