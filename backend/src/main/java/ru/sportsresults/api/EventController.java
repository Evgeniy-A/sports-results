package ru.sportsresults.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.EventDto;
import ru.sportsresults.api.dto.EventDetailsDto;
import ru.sportsresults.api.dto.EventFilterOptionsDto;
import ru.sportsresults.api.dto.PageResponse;
import ru.sportsresults.api.dto.CategoryOptionDto;
import ru.sportsresults.api.dto.RaceDto;
import ru.sportsresults.service.EventService;
import ru.sportsresults.service.EventDetailsService;
import ru.sportsresults.service.EventDocumentService;
import ru.sportsresults.service.ResultInquiryService;
import ru.sportsresults.api.dto.ResultInquiryLookupDto;
import ru.sportsresults.api.dto.VerifyResultInquiryRequest;
import ru.sportsresults.domain.EventPhase;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final EventDetailsService eventDetailsService;
    private final EventDocumentService eventDocumentService;
    private final ResultInquiryService resultInquiryService;

    public EventController(EventService eventService, EventDetailsService eventDetailsService,
                           EventDocumentService eventDocumentService,
                           ResultInquiryService resultInquiryService) {
        this.eventService = eventService;
        this.eventDetailsService = eventDetailsService;
        this.eventDocumentService = eventDocumentService;
        this.resultInquiryService = resultInquiryService;
    }

    @GetMapping
    public PageResponse<EventDto> listEvents(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long eventSeriesId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) EventPhase phase,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return eventService.searchPublishedEvents(year, eventSeriesId, city, date, phase, page, size);
    }

    @GetMapping("/filter-options")
    public EventFilterOptionsDto filterOptions(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long eventSeriesId,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) LocalDate date
    ) {
        return eventService.filterOptions(year, eventSeriesId, city, date);
    }

    @GetMapping("/{eventId}")
    public EventDetailsDto getEvent(@PathVariable Long eventId) {
        return eventDetailsService.getPublished(eventId);
    }

    @GetMapping("/slug/{eventSlug}")
    public EventDetailsDto getEventBySlug(@PathVariable String eventSlug) {
        return eventDetailsService.getPublished(eventSlug);
    }

    @GetMapping("/{eventId}/races")
    public List<RaceDto> listRaces(@PathVariable Long eventId) {
        return eventService.listPublishedEventRaces(eventId);
    }

    @GetMapping("/{eventId}/categories")
    public List<CategoryOptionDto> listCategories(
            @PathVariable Long eventId,
            @RequestParam(required = false) Long raceId
    ) {
        return eventService.listPublishedEventCategories(eventId, raceId);
    }

    @GetMapping("/{eventId}/result-inquiry")
    public ResultInquiryLookupDto resultInquiry(
            @PathVariable Long eventId,
            @RequestParam String bib
    ) {
        return resultInquiryService.lookup(eventId, bib);
    }

    @PostMapping("/{eventId}/result-inquiry/verify")
    public ResultInquiryLookupDto verifyResultInquiry(
            @PathVariable Long eventId,
            @Valid @RequestBody VerifyResultInquiryRequest request
    ) {
        return resultInquiryService.verify(eventId, request.bib(), request.birthDate());
    }

    @GetMapping("/{eventId}/documents")
    public List<ru.sportsresults.api.dto.EventDocumentDto> listDocuments(@PathVariable Long eventId) {
        return eventDocumentService.listPublic(eventId);
    }

    @GetMapping("/{eventId}/documents/{documentId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> documentContent(
            @PathVariable Long eventId, @PathVariable Long documentId) {
        EventDocumentService.DocumentContent content = eventDocumentService.openPublic(eventId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(content.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.filename(), StandardCharsets.UTF_8).build().toString())
                .body(content.resource());
    }
}
