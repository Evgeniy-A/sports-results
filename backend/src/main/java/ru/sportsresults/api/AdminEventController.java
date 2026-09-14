package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import ru.sportsresults.api.dto.CreateEventRequest;
import ru.sportsresults.api.dto.EventDto;
import ru.sportsresults.api.dto.ImportReportDto;
import ru.sportsresults.api.dto.ImportPreviewResponseDto;
import ru.sportsresults.api.dto.ImportApplyResponseDto;
import ru.sportsresults.domain.ImportOperationMode;
import ru.sportsresults.domain.EventPublicationStatus;
import ru.sportsresults.domain.ResultsPublicationStatus;
import ru.sportsresults.api.dto.PageResponse;
import ru.sportsresults.api.dto.RaceDto;
import ru.sportsresults.api.dto.UpdateEventRequest;
import ru.sportsresults.api.dto.UpdateEventPublicationRequest;
import ru.sportsresults.api.dto.ResultInquirySettingsDto;
import ru.sportsresults.api.dto.UpdateResultInquirySettingsRequest;
import ru.sportsresults.service.EventService;
import ru.sportsresults.service.ImportService;
import ru.sportsresults.service.ImportPreviewService;
import ru.sportsresults.service.ImportApplyService;
import ru.sportsresults.service.InvalidRequestException;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/events")
@SecurityRequirement(name = "basicAuth")
public class AdminEventController {

    private final EventService eventService;
    private final ImportService importService;
    private final ImportPreviewService importPreviewService;
    private final ImportApplyService importApplyService;

    public AdminEventController(
            EventService eventService,
            ImportService importService,
            ImportPreviewService importPreviewService,
            ImportApplyService importApplyService
    ) {
        this.eventService = eventService;
        this.importService = importService;
        this.importPreviewService = importPreviewService;
        this.importApplyService = importApplyService;
    }

    @GetMapping
    public PageResponse<EventDto> listEvents(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) EventPublicationStatus publicationStatus,
            @RequestParam(required = false) ResultsPublicationStatus resultsPublicationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        return eventService.searchAdminEvents(
                name, location, publicationStatus, resultsPublicationStatus, page, size
        );
    }

    @GetMapping("/{eventId}")
    public EventDto getEvent(@PathVariable Long eventId) {
        return eventService.getAdminEvent(eventId);
    }

    @GetMapping("/{eventId}/races")
    public List<RaceDto> listRaces(@PathVariable Long eventId) {
        return eventService.listAdminEventRaces(eventId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto createEvent(@Valid @RequestBody CreateEventRequest request) {
        return eventService.createEvent(request);
    }

    @PutMapping("/{eventId}/publication")
    public EventDto updatePublication(@PathVariable Long eventId,
                                      @Valid @RequestBody UpdateEventPublicationRequest request,
                                      Principal principal) {
        return eventService.updatePublication(eventId, request, principal.getName());
    }

    @PutMapping("/{eventId}")
    public EventDto updateEvent(
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateEventRequest request,
            Principal principal
    ) {
        return eventService.updateEvent(eventId, request, principal.getName());
    }

    @GetMapping("/{eventId}/result-inquiry")
    public ResultInquirySettingsDto getResultInquirySettings(@PathVariable Long eventId) {
        return eventService.getResultInquirySettings(eventId);
    }

    @PutMapping("/{eventId}/result-inquiry")
    public ResultInquirySettingsDto updateResultInquirySettings(
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateResultInquirySettingsRequest request,
            Principal principal
    ) {
        return eventService.updateResultInquirySettings(eventId, request, principal.getName());
    }

    @PostMapping(path = "/{eventId}/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReportDto importCsv(
            @PathVariable Long eventId,
            @RequestPart("file") MultipartFile file,
            Principal principal
    ) {
        try {
            return importService.importEvent(eventId, file.getOriginalFilename(), file.getBytes());
        } catch (IOException exception) {
            throw new InvalidRequestException("UPLOAD_READ_FAILED", "Uploaded file could not be read");
        }
    }

    @PostMapping(path = "/{eventId}/imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreviewResponseDto previewImport(
            @PathVariable Long eventId,
            @RequestPart("file") MultipartFile file,
            @RequestParam ImportOperationMode mode,
            @RequestParam List<Long> raceIds,
            @RequestParam(required = false) Integer rowLimit,
            Principal principal
    ) {
        try {
            return importPreviewService.preview(
                    eventId,
                    file.getOriginalFilename(),
                    file.getBytes(),
                    mode,
                    raceIds,
                    rowLimit,
                    principal.getName()
            );
        } catch (IOException exception) {
            throw new InvalidRequestException("UPLOAD_READ_FAILED", "Uploaded file could not be read");
        }
    }

    @PostMapping(path = "/{eventId}/imports/{operationId}/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportApplyResponseDto applyImport(
            @PathVariable Long eventId,
            @PathVariable UUID operationId,
            @RequestPart("file") MultipartFile file,
            Principal principal
    ) {
        try {
            return importApplyService.apply(eventId, operationId, file.getBytes(), principal.getName());
        } catch (IOException exception) {
            throw new InvalidRequestException("UPLOAD_READ_FAILED", "Uploaded file could not be read");
        }
    }
}
