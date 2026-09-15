package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.PageResponse;
import ru.sportsresults.api.dto.AdminResultDetailsDto;
import ru.sportsresults.api.dto.ResultListItemDto;
import ru.sportsresults.api.dto.UpdateRegistrationRequest;
import ru.sportsresults.api.dto.UpdateResultRequest;
import ru.sportsresults.domain.RankingBasis;
import ru.sportsresults.service.AdminResultService;
import ru.sportsresults.service.ResultQueryService;

@RestController
@RequestMapping("/api/admin")
@SecurityRequirement(name = "basicAuth")
public class AdminResultController {

    private final AdminResultService adminResultService;
    private final ResultQueryService resultQueryService;

    public AdminResultController(AdminResultService adminResultService, ResultQueryService resultQueryService) {
        this.adminResultService = adminResultService;
        this.resultQueryService = resultQueryService;
    }

    @GetMapping("/events/{eventId}/results")
    public PageResponse<ResultListItemDto> searchResults(
            @PathVariable Long eventId,
            @RequestParam(required = false) Long raceId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String bib,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long clusterId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "place") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "CHIP_TIME") RankingBasis rankingBasis
    ) {
        return resultQueryService.searchAdmin(
                eventId, raceId, name, bib, gender, categoryId, clusterId, status,
                page, size, sort, direction, rankingBasis
        );
    }

    @GetMapping("/results/{resultId}")
    public AdminResultDetailsDto getResult(@PathVariable Long resultId) {
        return resultQueryService.getAdminResult(resultId);
    }

    @PutMapping("/registrations/{registrationId}")
    public ResponseEntity<Void> updateRegistration(
            @PathVariable Long registrationId,
            @Valid @RequestBody UpdateRegistrationRequest request,
            Authentication authentication
    ) {
        adminResultService.updateRegistration(registrationId, request, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/results/{resultId}")
    public ResponseEntity<Void> updateResult(
            @PathVariable Long resultId,
            @Valid @RequestBody UpdateResultRequest request,
            Authentication authentication
    ) {
        adminResultService.updateResult(resultId, request, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
