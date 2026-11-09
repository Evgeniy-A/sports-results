package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.JournalShareDtos;
import ru.sportsresults.service.JournalShareAccessService;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/result-issue-journal-shares")
@SecurityRequirement(name = "basicAuth")
public class AdminJournalShareController {

    private final JournalShareAccessService service;

    public AdminJournalShareController(JournalShareAccessService service) {
        this.service = service;
    }

    @GetMapping
    public List<JournalShareDtos.Grant> list() {
        return service.list();
    }

    @PostMapping
    public JournalShareDtos.Created create(
            @Valid @RequestBody JournalShareDtos.CreateRequest request,
            Principal principal
    ) {
        return service.create(request, principal.getName());
    }

    @PostMapping("/{grantId}/revoke")
    public JournalShareDtos.Grant revoke(@PathVariable UUID grantId, Principal principal) {
        return service.revoke(grantId, principal.getName());
    }
}
