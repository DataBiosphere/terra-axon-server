package bio.terra.axonserver.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "axonserver.sam")
public record SamConfiguration(String basePath, String resourceId) {}
