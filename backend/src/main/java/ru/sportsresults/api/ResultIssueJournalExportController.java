package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ru.sportsresults.api.dto.ResultIssueJournalExportRequest;
import ru.sportsresults.service.ResultIssueJournalExportArtifact;
import ru.sportsresults.service.ResultIssueJournalXlsxExportService;

import java.io.InputStream;
import java.nio.file.Files;
import java.security.Principal;

@RestController
@RequestMapping("/api/admin/result-issue-requests/export")
@SecurityRequirement(name = "basicAuth")
public class ResultIssueJournalExportController {

    public static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final Logger LOGGER = LoggerFactory.getLogger(ResultIssueJournalExportController.class);

    private final ResultIssueJournalXlsxExportService service;

    public ResultIssueJournalExportController(ResultIssueJournalXlsxExportService service) {
        this.service = service;
    }

    @PostMapping(path = "/xlsx", consumes = MediaType.APPLICATION_JSON_VALUE, produces = XLSX_MEDIA_TYPE)
    @Operation(summary = "Export all matching global Result Issue journal rows to XLSX")
    public ResponseEntity<StreamingResponseBody> export(
            @Valid @RequestBody ResultIssueJournalExportRequest request,
            Principal principal
    ) {
        ResultIssueJournalExportArtifact artifact = service.export(request, principal.getName());
        StreamingResponseBody body = output -> {
            try (InputStream input = Files.newInputStream(artifact.path())) {
                input.transferTo(output);
            } finally {
                try {
                    artifact.delete();
                } catch (Exception exception) {
                    LOGGER.warn("Could not remove completed Result Issue journal temporary file");
                }
            }
        };

        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .contentLength(artifact.sizeBytes())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(artifact.filename()).build().toString()
                );
        if (artifact.shareBatchId() != null) {
            response.header("X-Share-Batch-Id", artifact.shareBatchId().toString());
        }
        if (artifact.shareExpiresAt() != null) {
            response.header("X-Share-Expires-At", artifact.shareExpiresAt().toString());
        }
        return response.body(body);
    }
}
