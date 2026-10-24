package ru.sportsresults.api;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.sportsresults.api.dto.CertificateAvailabilityDto;
import ru.sportsresults.api.dto.GenerateCertificateRequest;
import ru.sportsresults.service.CertificateService;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/results/{resultId}/certificates")
public class CertificateController {
    private final CertificateService service;

    public CertificateController(CertificateService service) { this.service = service; }

    @GetMapping
    public CertificateAvailabilityDto availability(@PathVariable Long resultId) {
        return service.availability(resultId);
    }

    @PostMapping("/{templateId}/generate")
    public ResponseEntity<byte[]> generate(@PathVariable Long resultId, @PathVariable Long templateId,
            @Valid @RequestBody GenerateCertificateRequest request) {
        CertificateService.GeneratedCertificate certificate = service.generate(resultId, templateId, request.fields());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .contentLength(certificate.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(certificate.filename(), StandardCharsets.UTF_8).build().toString())
                .body(certificate.bytes());
    }
}
