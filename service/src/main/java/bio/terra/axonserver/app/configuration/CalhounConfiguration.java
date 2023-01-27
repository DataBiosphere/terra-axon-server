package bio.terra.axonserver.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "axonserver.calhoun")
public record CalhounConfiguration(String basePath) {}
