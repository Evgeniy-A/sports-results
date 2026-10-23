package ru.sportsresults.api;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.sportsresults.api.dto.CurrentAdminUserDto;
import ru.sportsresults.service.AdminUserService;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/me")
@SecurityRequirement(name = "basicAuth")
public class AdminIdentityController {

    private final AdminUserService service;

    public AdminIdentityController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    public CurrentAdminUserDto current(Principal principal) {
        return service.current(principal.getName());
    }
}
