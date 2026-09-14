package ru.sportsresults.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.sportsresults.api.dto.EventDocumentDto;
import ru.sportsresults.api.dto.UpdateEventDocumentRequest;
import ru.sportsresults.domain.*;
import ru.sportsresults.repository.AdminChangeLogRepository;
import ru.sportsresults.repository.EventDocumentRepository;
import ru.sportsresults.storage.FileStorageService;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Service
public class EventDocumentService {
    private static final String PDF_CONTENT_TYPE = "application/pdf";

    private final EventService eventService;
    private final EventDocumentRepository repository;
    private final FileStorageService storage;
    private final AdminChangeLogRepository auditRepository;
    private final long maxSizeBytes;

    public EventDocumentService(EventService eventService, EventDocumentRepository repository,
                                FileStorageService storage, AdminChangeLogRepository auditRepository,
                                @Value("${app.documents.max-size-bytes:10485760}") long maxSizeBytes) {
        this.eventService = eventService;
        this.repository = repository;
        this.storage = storage;
        this.auditRepository = auditRepository;
        this.maxSizeBytes = maxSizeBytes;
    }

    @Transactional(readOnly = true)
    public List<EventDocumentDto> listPublic(Long eventId) {
        requirePublishedEvent(eventId);
        return repository.findAllByEventIdAndPublicDocumentTrueOrderByDisplayOrderAscIdAsc(eventId).stream()
                .map(EventDocumentService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<EventDocumentDto> listAdmin(Long eventId) {
        eventService.requireEvent(eventId);
        return repository.findAllByEventIdOrderByDisplayOrderAscIdAsc(eventId).stream()
                .map(EventDocumentService::toDto).toList();
    }

    @Transactional
    public EventDocumentDto upload(Long eventId, EventDocumentType type, String displayName, int displayOrder,
                                   boolean publicDocument, String originalFilename, String contentType,
                                   byte[] content, String actor) {
        Event event = eventService.requireEvent(eventId);
        validate(displayName, originalFilename, contentType, content);
        String storageKey = storage.store(content);
        try {
            EventDocument document = new EventDocument();
            document.setEvent(event);
            document.setType(type);
            document.setDisplayName(displayName.strip());
            document.setOriginalFilename(safeFilename(originalFilename));
            document.setStorageKey(storageKey);
            document.setContentType(PDF_CONTENT_TYPE);
            document.setSizeBytes(content.length);
            document.setSha256(sha256(content));
            document.setDisplayOrder(displayOrder);
            document.setPublicDocument(publicDocument);
            document.setUploadedAt(Instant.now());
            document.setUploadedBy(actor);
            EventDocument saved = repository.saveAndFlush(document);
            audit(actor, saved.getId(), "uploaded", null, summary(saved));
            return toDto(saved);
        } catch (RuntimeException exception) {
            storage.delete(storageKey);
            throw exception;
        }
    }

    @Transactional
    public EventDocumentDto update(Long eventId, Long documentId, UpdateEventDocumentRequest request, String actor) {
        EventDocument document = requireOwned(eventId, documentId);
        String old = summary(document);
        document.setType(request.type());
        document.setDisplayName(request.displayName().strip());
        document.setDisplayOrder(request.displayOrder());
        document.setPublicDocument(request.publicDocument());
        EventDocument saved = repository.save(document);
        audit(actor, documentId, "updated", old, summary(saved));
        return toDto(saved);
    }

    @Transactional
    public void delete(Long eventId, Long documentId, String actor) {
        EventDocument document = requireOwned(eventId, documentId);
        repository.delete(document);
        repository.flush();
        storage.delete(document.getStorageKey());
        audit(actor, documentId, "deleted", summary(document), null);
    }

    @Transactional(readOnly = true)
    public DocumentContent openPublic(Long eventId, Long documentId) {
        requirePublishedEvent(eventId);
        EventDocument document = repository.findByIdAndEventIdAndPublicDocumentTrue(documentId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_DOCUMENT_NOT_FOUND", "Public event document not found"));
        return content(document);
    }

    @Transactional(readOnly = true)
    public DocumentContent openAdmin(Long eventId, Long documentId) {
        eventService.requireEvent(eventId);
        return content(requireOwned(eventId, documentId));
    }

    private DocumentContent content(EventDocument document) {
        InputStream stream = storage.open(document.getStorageKey());
        Resource resource = new InputStreamResource(stream);
        return new DocumentContent(resource, document.getOriginalFilename(), document.getSizeBytes());
    }

    private EventDocument requireOwned(Long eventId, Long documentId) {
        return repository.findByIdAndEventId(documentId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("EVENT_DOCUMENT_NOT_FOUND", "Event document not found"));
    }

    private Event requirePublishedEvent(Long eventId) {
        Event event = eventService.requireEvent(eventId);
        if (event.getPublicationStatus() != EventPublicationStatus.PUBLISHED || !event.getEventSeries().isActive()) {
            throw new ResourceNotFoundException("EVENT_NOT_FOUND", "Published event not found");
        }
        return event;
    }

    private void validate(String displayName, String originalFilename, String contentType, byte[] content) {
        if (displayName == null || displayName.isBlank() || displayName.length() > 255) {
            throw new InvalidRequestException("INVALID_DOCUMENT_NAME", "Document display name is required");
        }
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidRequestException("INVALID_DOCUMENT_FILENAME", "Original filename is required");
        }
        if (content.length == 0 || content.length > maxSizeBytes) {
            throw new InvalidRequestException("INVALID_DOCUMENT_SIZE", "PDF must be non-empty and no larger than configured maximum");
        }
        if (!PDF_CONTENT_TYPE.equalsIgnoreCase(contentType) || content.length < 5
                || content[0] != '%' || content[1] != 'P' || content[2] != 'D' || content[3] != 'F' || content[4] != '-') {
            throw new InvalidRequestException("INVALID_DOCUMENT_TYPE", "Only valid PDF documents are accepted");
        }
    }

    private static String safeFilename(String originalFilename) {
        String safe = originalFilename.replace('\\', '/');
        safe = safe.substring(safe.lastIndexOf('/') + 1).strip();
        if (safe.isBlank() || safe.length() > 255) {
            throw new InvalidRequestException("INVALID_DOCUMENT_FILENAME", "Invalid document filename");
        }
        return safe;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static EventDocumentDto toDto(EventDocument document) {
        return new EventDocumentDto(document.getId(), document.getType(), document.getDisplayName(),
                document.getOriginalFilename(), document.getContentType(), document.getSizeBytes(), document.getSha256(),
                document.getDisplayOrder(), document.isPublicDocument(), document.getUploadedAt(),
                "/api/events/" + document.getEvent().getId() + "/documents/" + document.getId() + "/content");
    }

    private static String summary(EventDocument document) {
        return document.getType() + ":" + document.getDisplayName() + ":" + document.getOriginalFilename()
                + ":" + document.getSizeBytes() + ":" + document.getSha256();
    }

    private void audit(String actor, Long id, String field, String oldValue, String newValue) {
        AdminChangeLog log = new AdminChangeLog();
        log.setActor(actor);
        log.setEntityType(AuditEntityType.EVENT_DOCUMENT);
        log.setEntityId(id);
        log.setFieldName(field);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        auditRepository.save(log);
    }

    public record DocumentContent(Resource resource, String filename, long sizeBytes) {
    }
}
