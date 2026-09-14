package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.RacePublicSettingsDto;
import ru.sportsresults.api.dto.UpdateRacePublicSettingsRequest;
import ru.sportsresults.service.RacePublicSettingsService;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/races/{raceId}/public-settings")
@SecurityRequirement(name = "basicAuth")
public class AdminRacePublicSettingsController {

    private final RacePublicSettingsService service;

    public AdminRacePublicSettingsController(RacePublicSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public RacePublicSettingsDto get(@PathVariable Long raceId) {
        return service.get(raceId);
    }

    @PutMapping
    public RacePublicSettingsDto update(
            @PathVariable Long raceId,
            @Valid @RequestBody UpdateRacePublicSettingsRequest request,
            Principal principal
    ) {
        return service.update(raceId, request, principal.getName());
    }
}
