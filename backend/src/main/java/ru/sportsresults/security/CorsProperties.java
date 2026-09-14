package ru.sportsresults.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream()
                        .map(String::strip)
                        .filter(origin -> !origin.isEmpty())
                        .toList();
        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("app.cors.allowed-origins must contain at least one origin");
        }
        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException("Wildcard CORS origins are not allowed");
        }
    }
}
