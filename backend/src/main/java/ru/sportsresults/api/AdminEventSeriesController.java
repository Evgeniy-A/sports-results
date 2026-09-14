package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.CreateEventSeriesRequest;
import ru.sportsresults.api.dto.EventSeriesDto;
import ru.sportsresults.api.dto.UpdateEventSeriesRequest;
import ru.sportsresults.service.EventSeriesService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/event-series")
@SecurityRequirement(name = "basicAuth")
public class AdminEventSeriesController {
    private final EventSeriesService service;

    public AdminEventSeriesController(EventSeriesService service) {
        this.service = service;
    }

    @GetMapping
    public List<EventSeriesDto> list() {
        return service.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventSeriesDto create(@Valid @RequestBody CreateEventSeriesRequest request) {
        return service.create(request);
    }

    @PutMapping("/{seriesId}")
    public EventSeriesDto update(
            @PathVariable Long seriesId,
            @Valid @RequestBody UpdateEventSeriesRequest request,
            Principal principal
    ) {
        return service.update(seriesId, request, principal.getName());
    }
}
