package bio.terra.axonserver.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "axonserver.cli")
public class CliVersionConfiguration {
  private String oldestVersion = "none";
  private String latestVersion = "none";

  public String getOldestVersion() {
    return oldestVersion;
  }

  public void setOldestVersion(String oldestVersion) {
    this.oldestVersion = oldestVersion;
  }

  public String getLatestVersion() {
    return latestVersion;
  }

  public void setLatestVersion(String latestVersion) {
    this.latestVersion = latestVersion;
  }
}
