package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.service.ResourceNotFoundException;
import ru.sportsresults.service.ResultIssueAttachmentShareService;
import ru.sportsresults.service.ResultIssueShareStorageUnavailableException;

import java.net.URI;

@RestController
@RequestMapping("/share/attachments")
public class PublicResultIssueAttachmentShareController {

    private final ResultIssueAttachmentShareService service;

    public PublicResultIssueAttachmentShareController(ResultIssueAttachmentShareService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Open an attachment through an opaque external share token")
    public ResponseEntity<Void> open(@PathVariable String token) {
        try {
            URI location = service.resolve(token);
            return response(HttpStatus.FOUND).location(location).build();
        } catch (ResourceNotFoundException exception) {
            return response(HttpStatus.NOT_FOUND).build();
        } catch (ResultIssueShareStorageUnavailableException exception) {
            return response(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    private static ResponseEntity.BodyBuilder response(HttpStatus status) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff");
    }
}
