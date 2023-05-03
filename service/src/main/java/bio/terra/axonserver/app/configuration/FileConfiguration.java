package bio.terra.axonserver.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "axonserver.file")
public record FileConfiguration(Integer signedUrlExpirationMinutes) {}
