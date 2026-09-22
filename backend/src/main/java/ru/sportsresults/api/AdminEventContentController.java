package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.sportsresults.api.dto.*;
import ru.sportsresults.domain.EventDocumentType;
import ru.sportsresults.service.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/events/{eventId}")
@SecurityRequirement(name = "basicAuth")
public class AdminEventContentController {
    private final EventContentService contentService;
    private final RaceAdminService raceService;
    private final StartClusterService clusterService;
    private final EventDocumentService documentService;
    private final RaceResultsPublicationService raceResultsPublicationService;

    public AdminEventContentController(EventContentService contentService, RaceAdminService raceService,
                                       StartClusterService clusterService, EventDocumentService documentService,
                                       RaceResultsPublicationService raceResultsPublicationService) {
        this.contentService = contentService;
        this.raceService = raceService;
        this.clusterService = clusterService;
        this.documentService = documentService;
        this.raceResultsPublicationService = raceResultsPublicationService;
    }

    @GetMapping("/participant-info")
    public EventParticipantInfoDto participantInfo(@PathVariable Long eventId) {
        return contentService.getParticipantInfo(eventId);
    }

    @PutMapping("/participant-info")
    public EventParticipantInfoDto updateParticipantInfo(@PathVariable Long eventId,
            @Valid @RequestBody UpdateEventParticipantInfoRequest request, Principal principal) {
        return contentService.updateParticipantInfo(eventId, request, principal.getName());
    }

    @GetMapping("/info-blocks")
    public List<EventInfoBlockDto> infoBlocks(@PathVariable Long eventId) {
        return contentService.listInfoBlocks(eventId);
    }

    @PostMapping("/info-blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public EventInfoBlockDto createInfoBlock(@PathVariable Long eventId,
            @Valid @RequestBody UpsertEventInfoBlockRequest request, Principal principal) {
        return contentService.createInfoBlock(eventId, request, principal.getName());
    }

    @PutMapping("/info-blocks/{blockId}")
    public EventInfoBlockDto updateInfoBlock(@PathVariable Long eventId, @PathVariable Long blockId,
            @Valid @RequestBody UpsertEventInfoBlockRequest request, Principal principal) {
        return contentService.updateInfoBlock(eventId, blockId, request, principal.getName());
    }

    @DeleteMapping("/info-blocks/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInfoBlock(@PathVariable Long eventId, @PathVariable Long blockId, Principal principal) {
        contentService.deleteInfoBlock(eventId, blockId, principal.getName());
    }

    @GetMapping("/schedule")
    public List<EventScheduleItemDto> schedule(@PathVariable Long eventId) {
        return contentService.listSchedule(eventId);
    }

    @PostMapping("/schedule")
    @ResponseStatus(HttpStatus.CREATED)
    public EventScheduleItemDto createScheduleItem(@PathVariable Long eventId,
            @Valid @RequestBody UpsertEventScheduleItemRequest request, Principal principal) {
        return contentService.createScheduleItem(eventId, request, principal.getName());
    }

    @PutMapping("/schedule/{itemId}")
    public EventScheduleItemDto updateScheduleItem(@PathVariable Long eventId, @PathVariable Long itemId,
            @Valid @RequestBody UpsertEventScheduleItemRequest request, Principal principal) {
        return contentService.updateScheduleItem(eventId, itemId, request, principal.getName());
    }

    @DeleteMapping("/schedule/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteScheduleItem(@PathVariable Long eventId, @PathVariable Long itemId, Principal principal) {
        contentService.deleteScheduleItem(eventId, itemId, principal.getName());
    }

    @PostMapping("/races")
    @ResponseStatus(HttpStatus.CREATED)
    public RaceDto createRace(@PathVariable Long eventId, @Valid @RequestBody UpsertRaceRequest request, Principal principal) {
        return raceService.create(eventId, request, principal.getName());
    }

    @PutMapping("/races/{raceId}")
    public RaceDto updateRace(@PathVariable Long eventId, @PathVariable Long raceId,
                              @Valid @RequestBody UpsertRaceRequest request, Principal principal) {
        return raceService.update(eventId, raceId, request, principal.getName());
    }

    @DeleteMapping("/races/{raceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRace(@PathVariable Long eventId, @PathVariable Long raceId, Principal principal) {
        raceService.delete(eventId, raceId, principal.getName());
    }

    @PostMapping("/races/{raceId}/results/draft")
    public RaceResultsPublicationDto draftRaceResults(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            @Valid @RequestBody(required = false) RaceResultsDraftRequest request,
            Principal principal
    ) {
        return raceResultsPublicationService.draft(
                eventId, raceId, request == null ? null : request.reason(), principal.getName()
        );
    }

    @PostMapping("/races/{raceId}/results/publish")
    public RaceResultsPublicationDto publishRaceResults(
            @PathVariable Long eventId,
            @PathVariable Long raceId,
            Principal principal
    ) {
        return raceResultsPublicationService.publish(eventId, raceId, principal.getName());
    }

    @GetMapping("/races/{raceId}/clusters")
    public List<StartClusterDto> clusters(@PathVariable Long eventId, @PathVariable Long raceId) {
        return clusterService.list(eventId, raceId);
    }

    @PostMapping("/races/{raceId}/clusters")
    @ResponseStatus(HttpStatus.CREATED)
    public StartClusterDto createCluster(@PathVariable Long eventId, @PathVariable Long raceId,
            @Valid @RequestBody UpsertStartClusterRequest request, Principal principal) {
        return clusterService.create(eventId, raceId, request, principal.getName());
    }

    @PutMapping("/races/{raceId}/clusters/{clusterId}")
    public StartClusterDto updateCluster(@PathVariable Long eventId, @PathVariable Long raceId, @PathVariable Long clusterId,
            @Valid @RequestBody UpsertStartClusterRequest request, Principal principal) {
        return clusterService.update(eventId, raceId, clusterId, request, principal.getName());
    }

    @DeleteMapping("/races/{raceId}/clusters/{clusterId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCluster(@PathVariable Long eventId, @PathVariable Long raceId, @PathVariable Long clusterId,
                              Principal principal) {
        clusterService.delete(eventId, raceId, clusterId, principal.getName());
    }

    @GetMapping("/documents")
    public List<EventDocumentDto> documents(@PathVariable Long eventId) {
        return documentService.listAdmin(eventId);
    }

    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public EventDocumentDto uploadDocument(@PathVariable Long eventId,
            @RequestParam EventDocumentType type, @RequestParam String displayName,
            @RequestParam(defaultValue = "0") int displayOrder,
            @RequestParam(defaultValue = "true") boolean publicDocument,
            @RequestPart("file") MultipartFile file, Principal principal) {
        try {
            return documentService.upload(eventId, type, displayName, displayOrder, publicDocument,
                    file.getOriginalFilename(), file.getContentType(), file.getBytes(), principal.getName());
        } catch (IOException exception) {
            throw new InvalidRequestException("UPLOAD_READ_FAILED", "Uploaded file could not be read");
        }
    }

    @PutMapping("/documents/{documentId}")
    public EventDocumentDto updateDocument(@PathVariable Long eventId, @PathVariable Long documentId,
            @Valid @RequestBody UpdateEventDocumentRequest request, Principal principal) {
        return documentService.update(eventId, documentId, request, principal.getName());
    }

    @DeleteMapping("/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable Long eventId, @PathVariable Long documentId, Principal principal) {
        documentService.delete(eventId, documentId, principal.getName());
    }

    @GetMapping("/documents/{documentId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> documentContent(
            @PathVariable Long eventId, @PathVariable Long documentId) {
        EventDocumentService.DocumentContent content = documentService.openAdmin(eventId, documentId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.filename(), StandardCharsets.UTF_8).build().toString())
                .body(content.resource());
    }
}
