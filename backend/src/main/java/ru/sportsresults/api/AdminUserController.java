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
import ru.sportsresults.api.dto.AdminUserDto;
import ru.sportsresults.api.dto.AdminUserEventAccessDto;
import ru.sportsresults.api.dto.CreateAdminUserRequest;
import ru.sportsresults.api.dto.ReplaceAdminUserEventsRequest;
import ru.sportsresults.api.dto.UpdateAdminUserPasswordRequest;
import ru.sportsresults.api.dto.UpdateAdminUserRequest;
import ru.sportsresults.service.AdminUserService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@SecurityRequirement(name = "basicAuth")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminUserDto> list() { return service.list(); }

    @GetMapping("/{userId}")
    public AdminUserDto get(@PathVariable Long userId) { return service.get(userId); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserDto create(
            @Valid @RequestBody CreateAdminUserRequest request,
            Principal principal
    ) {
        return service.create(request, principal.getName());
    }

    @PutMapping("/{userId}")
    public AdminUserDto update(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateAdminUserRequest request,
            Principal principal
    ) {
        return service.update(userId, request, principal.getName());
    }

    @PutMapping("/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updatePassword(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateAdminUserPasswordRequest request,
            Principal principal
    ) {
        service.updatePassword(userId, request, principal.getName());
    }

    @GetMapping("/{userId}/events")
    public AdminUserEventAccessDto events(@PathVariable Long userId) {
        return service.events(userId);
    }

    @PutMapping("/{userId}/events")
    public AdminUserEventAccessDto replaceEvents(
            @PathVariable Long userId,
            @Valid @RequestBody ReplaceAdminUserEventsRequest request,
            Principal principal
    ) {
        return service.replaceEvents(userId, request, principal.getName());
    }
}
