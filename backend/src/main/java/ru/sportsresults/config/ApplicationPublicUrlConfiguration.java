package ru.sportsresults.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ApplicationPublicUrlProperties.class)
public class ApplicationPublicUrlConfiguration {
}
