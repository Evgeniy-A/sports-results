package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.AwardPolicyDto;
import ru.sportsresults.api.dto.UpdateAwardPolicyRequest;
import ru.sportsresults.service.AwardPolicyService;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/races/{raceId}/award-policy")
@SecurityRequirement(name = "basicAuth")
public class AdminAwardPolicyController {
    private final AwardPolicyService service;

    public AdminAwardPolicyController(AwardPolicyService service) {
        this.service = service;
    }

    @GetMapping
    public AwardPolicyDto get(@PathVariable Long raceId) {
        return service.get(raceId);
    }

    @PutMapping
    public AwardPolicyDto update(
            @PathVariable Long raceId,
            @Valid @RequestBody UpdateAwardPolicyRequest request,
            Principal principal
    ) {
        return service.upsert(raceId, request, principal.getName());
    }
}
