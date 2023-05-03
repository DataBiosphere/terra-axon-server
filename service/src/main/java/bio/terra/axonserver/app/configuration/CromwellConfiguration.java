package bio.terra.axonserver.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "axonserver.cromwell")
public record CromwellConfiguration(String basePath) {}
