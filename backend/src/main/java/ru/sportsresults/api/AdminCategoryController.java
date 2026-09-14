package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.AdminCategoryDto;
import ru.sportsresults.api.dto.UpsertCategoryRequest;
import ru.sportsresults.service.CategoryAdminService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/races/{raceId}/categories")
@SecurityRequirement(name = "basicAuth")
public class AdminCategoryController {

    private final CategoryAdminService service;

    public AdminCategoryController(CategoryAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminCategoryDto> list(@PathVariable Long raceId) {
        return service.list(raceId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminCategoryDto create(
            @PathVariable Long raceId,
            @Valid @RequestBody UpsertCategoryRequest request,
            Principal principal
    ) {
        return service.create(raceId, request, principal.getName());
    }

    @PutMapping("/{categoryId}")
    public AdminCategoryDto update(
            @PathVariable Long raceId,
            @PathVariable Long categoryId,
            @Valid @RequestBody UpsertCategoryRequest request,
            Principal principal
    ) {
        return service.update(raceId, categoryId, request, principal.getName());
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long raceId,
            @PathVariable Long categoryId,
            Principal principal
    ) {
        service.delete(raceId, categoryId, principal.getName());
        return ResponseEntity.noContent().build();
    }
}
