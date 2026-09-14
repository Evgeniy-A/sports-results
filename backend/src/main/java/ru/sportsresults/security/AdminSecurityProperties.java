package ru.sportsresults.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.admin")
public record AdminSecurityProperties(
        @NotBlank @Size(max = 160) String username,
        @NotBlank String password
) {
}
