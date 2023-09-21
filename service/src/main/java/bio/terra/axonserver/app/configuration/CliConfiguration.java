package bio.terra.axonserver.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "axonserver.cli")
public class CliConfiguration {
  private String oldestVersion = "none";
  private String latestVersion = "none";
  private String dockerRepoHost = "none";
  private String dockerImageName = "none";
  private String dockerImageTag = "none";
  private String distributionPath = "none";

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

  public String getDockerRepoHost() {
    return dockerRepoHost;
  }

  public void setDockerRepoHost(String dockerRepoHost) {
    this.dockerRepoHost = dockerRepoHost;
  }

  public String getDockerImageName() {
    return dockerImageName;
  }

  public void setDockerImageName(String dockerImageName) {
    this.dockerImageName = dockerImageName;
  }

  public String getDockerImageTag() {
    return dockerImageTag;
  }

  public void setDockerImageTag(String dockerImageTag) {
    this.dockerImageTag = dockerImageTag;
  }

  public String getDistributionPath() {
    return distributionPath;
  }

  public void setDistributionPath(String distributionPath) {
    this.distributionPath = distributionPath;
  }
}
