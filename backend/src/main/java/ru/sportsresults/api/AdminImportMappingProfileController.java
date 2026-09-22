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
import ru.sportsresults.api.dto.ImportMappingProfileDto;
import ru.sportsresults.api.dto.UpsertImportMappingProfileRequest;
import ru.sportsresults.service.ImportMappingProfileService;

import java.util.List;

@RestController
@RequestMapping("/api/admin/import-mapping-profiles")
@SecurityRequirement(name = "basicAuth")
public class AdminImportMappingProfileController {

    private final ImportMappingProfileService service;

    public AdminImportMappingProfileController(ImportMappingProfileService service) {
        this.service = service;
    }

    @GetMapping
    public List<ImportMappingProfileDto> list() { return service.list(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportMappingProfileDto create(@Valid @RequestBody UpsertImportMappingProfileRequest request) {
        return service.create(request);
    }

    @PutMapping("/{profileId}")
    public ImportMappingProfileDto update(
            @PathVariable Long profileId,
            @Valid @RequestBody UpsertImportMappingProfileRequest request
    ) {
        return service.update(profileId, request);
    }

    @DeleteMapping("/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long profileId) { service.delete(profileId); }
}
