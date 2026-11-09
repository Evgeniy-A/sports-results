package ru.sportsresults.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.PublicAppealEventDto;
import ru.sportsresults.service.PublicAppealCatalogService;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/appeal-events")
public class PublicAppealController {

    private final PublicAppealCatalogService service;

    public PublicAppealController(PublicAppealCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PublicAppealEventDto>> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(service.openEvents());
    }
}
