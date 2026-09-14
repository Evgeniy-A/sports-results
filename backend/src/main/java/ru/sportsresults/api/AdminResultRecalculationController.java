package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.ResultRecalculationApplyDto;
import ru.sportsresults.api.dto.ResultRecalculationPreviewDto;
import ru.sportsresults.api.dto.ResultRecalculationPreviewRequest;
import ru.sportsresults.service.ResultRecalculationService;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/events/{eventId}/result-recalculations")
@SecurityRequirement(name = "basicAuth")
public class AdminResultRecalculationController {

    private final ResultRecalculationService service;

    public AdminResultRecalculationController(ResultRecalculationService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public ResultRecalculationPreviewDto preview(
            @PathVariable Long eventId,
            @Valid @RequestBody ResultRecalculationPreviewRequest request,
            Principal principal
    ) {
        return service.preview(eventId, request.raceIds(), request.rowLimit(), principal.getName());
    }

    @PostMapping("/{operationId}/apply")
    public ResultRecalculationApplyDto apply(
            @PathVariable Long eventId,
            @PathVariable UUID operationId,
            Principal principal
    ) {
        return service.apply(eventId, operationId, principal.getName());
    }
}
