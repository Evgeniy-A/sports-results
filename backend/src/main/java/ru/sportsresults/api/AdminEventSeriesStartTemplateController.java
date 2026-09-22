package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.EventSeriesStartAwardPolicyTemplateDto;
import ru.sportsresults.api.dto.EventSeriesStartCategoryTemplateDto;
import ru.sportsresults.api.dto.EventSeriesStartTemplateDto;
import ru.sportsresults.api.dto.ReorderEventSeriesStartTemplatesRequest;
import ru.sportsresults.api.dto.UpdateAwardPolicyRequest;
import ru.sportsresults.api.dto.UpsertCategoryRequest;
import ru.sportsresults.api.dto.UpsertEventSeriesStartTemplateRequest;
import ru.sportsresults.service.EventSeriesStartTemplateService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/event-series/{seriesId}/start-templates")
@SecurityRequirement(name = "basicAuth")
public class AdminEventSeriesStartTemplateController {
    private final EventSeriesStartTemplateService service;

    public AdminEventSeriesStartTemplateController(EventSeriesStartTemplateService service) {
        this.service = service;
    }

    @GetMapping
    public List<EventSeriesStartTemplateDto> list(@PathVariable Long seriesId) {
        return service.list(seriesId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventSeriesStartTemplateDto create(
            @PathVariable Long seriesId,
            @Valid @RequestBody UpsertEventSeriesStartTemplateRequest request
    ) {
        return service.create(seriesId, request);
    }

    @PutMapping("/{templateStartId}")
    public EventSeriesStartTemplateDto update(
            @PathVariable Long seriesId,
            @PathVariable Long templateStartId,
            @Valid @RequestBody UpsertEventSeriesStartTemplateRequest request
    ) {
        return service.update(seriesId, templateStartId, request);
    }

    @PutMapping("/reorder")
    public List<EventSeriesStartTemplateDto> reorder(
            @PathVariable Long seriesId,
            @Valid @RequestBody ReorderEventSeriesStartTemplatesRequest request
    ) {
        return service.reorder(seriesId, request);
    }

    @DeleteMapping("/{templateStartId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long seriesId, @PathVariable Long templateStartId) {
        service.delete(seriesId, templateStartId);
    }

    @GetMapping("/{templateStartId}/award-policy")
    public EventSeriesStartAwardPolicyTemplateDto getAwardPolicy(
            @PathVariable Long seriesId,
            @PathVariable Long templateStartId
    ) {
        return service.getAwardPolicy(seriesId, templateStartId);
    }

    @PutMapping("/{templateStartId}/award-policy")
    public EventSeriesStartAwardPolicyTemplateDto upsertAwardPolicy(
            @PathVariable Long seriesId,
            @PathVariable Long templateStartId,
            @Valid @RequestBody UpdateAwardPolicyRequest request
    ) {
        return service.upsertAwardPolicy(seriesId, templateStartId, request);
    }

    @DeleteMapping("/{templateStartId}/award-policy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAwardPolicy(
            @PathVariable Long seriesId,
            @PathVariable Long templateStartId
    ) {
        service.deleteAwardPolicy(seriesId, templateStartId);
    }

    @GetMapping("/{templateStartId}/categories")
    public List<EventSeriesStartCategoryTemplateDto> listCategories(
            @PathVariable Long seriesId,
            @PathVariable Long templateStartId
    ) {
        return service.listCategories(seriesId, templateStartId);
    }

    @PostMapping("/{templateStartId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public EventSeriesStartCategoryTemplateDto createCategory(
            @PathVariable Long seriesId,
            @PathVariable Long templateStartId,
            @Valid @RequestBody UpsertCategoryRequest request
    ) {
        return service.createCategory(seriesId, templateStartId, request);
    }

    @PutMapping("/{templateStartId}/categories/{categoryId}")
    public EventSeriesStartCategoryTemplateDto updateCategory(
            @PathVariable Long seriesId,
            @PathVariable Long templateStartId,
            @PathVariable Long categoryId,
            @Valid @RequestBody UpsertCategoryRequest request
    ) {
        return service.updateCategory(seriesId, templateStartId, categoryId, request);
    }

    @DeleteMapping("/{templateStartId}/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(
            @PathVariable Long seriesId,
            @PathVariable Long templateStartId,
            @PathVariable Long categoryId
    ) {
        service.deleteCategory(seriesId, templateStartId, categoryId);
    }
}
