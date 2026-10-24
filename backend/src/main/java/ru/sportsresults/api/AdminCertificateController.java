package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.sportsresults.api.dto.*;
import ru.sportsresults.service.CertificateService;
import ru.sportsresults.service.InvalidRequestException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/events/{eventId}/certificates")
@SecurityRequirement(name = "basicAuth")
@PreAuthorize("@eventAccessService.canAccessEvent(authentication, #eventId)")
public class AdminCertificateController {
    private final CertificateService service;

    public AdminCertificateController(CertificateService service) { this.service = service; }

    @GetMapping("/settings")
    public CertificateSettingsDto settings(@PathVariable Long eventId) { return service.adminSettings(eventId); }

    @PutMapping("/settings")
    public CertificateSettingsDto updateSettings(@PathVariable Long eventId,
            @Valid @RequestBody UpdateCertificateSettingsRequest request, Principal principal) {
        return service.updateSettings(eventId, request, principal.getName());
    }

    @GetMapping("/templates")
    public List<CertificateTemplateDto> templates(@PathVariable Long eventId) {
        return service.listTemplates(eventId);
    }

    @PostMapping(path = "/templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CertificateTemplateDto upload(@PathVariable Long eventId,
            @RequestParam String displayName,
            @RequestParam(defaultValue = "0") int displayOrder,
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(required = false) Set<Long> raceIds,
            @RequestPart("file") MultipartFile file,
            Principal principal) {
        try {
            return service.upload(eventId, displayName, displayOrder, active,
                    raceIds == null ? Set.of() : raceIds, file.getOriginalFilename(), file.getContentType(),
                    file.getBytes(), principal.getName());
        } catch (IOException exception) {
            throw new InvalidRequestException("UPLOAD_READ_FAILED", "Uploaded file could not be read");
        }
    }

    @PutMapping("/templates/{templateId}")
    public CertificateTemplateDto update(@PathVariable Long eventId, @PathVariable Long templateId,
            @Valid @RequestBody UpdateCertificateTemplateRequest request, Principal principal) {
        return service.update(eventId, templateId, request, principal.getName());
    }

    @DeleteMapping("/templates/{templateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long eventId, @PathVariable Long templateId, Principal principal) {
        service.delete(eventId, templateId, principal.getName());
    }

    @PostMapping("/templates/{templateId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long eventId, @PathVariable Long templateId,
            @Valid @RequestBody GenerateCertificateRequest request) {
        CertificateService.GeneratedCertificate certificate = service.preview(eventId, templateId, request.fields());
        return pdf(certificate);
    }

    private static ResponseEntity<byte[]> pdf(CertificateService.GeneratedCertificate certificate) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .contentLength(certificate.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(certificate.filename(), StandardCharsets.UTF_8).build().toString())
                .body(certificate.bytes());
    }
}
