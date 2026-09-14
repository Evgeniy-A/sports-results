package ru.sportsresults.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ResultIssueAttachmentProperties.class)
public class ResultIssueAttachmentConfiguration {
}
