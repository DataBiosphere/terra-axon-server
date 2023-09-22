package bio.terra.axonserver.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "axonserver.cli")
public class CliConfiguration {
  private String oldestVersion = "none";
  private String latestVersion = "none";
  private String dockerRepoHost = "none";
  private String dockerRepoImageName = "none";
  private String dockerRepoImageTag = "none";
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

  public String getDockerRepoImageName() {
    return dockerRepoImageName;
  }

  public void setDockerRepoImageName(String dockerRepoImageName) {
    this.dockerRepoImageName = dockerRepoImageName;
  }

  public String getDockerRepoImageTag() {
    return dockerRepoImageTag;
  }

  public void setDockerRepoImageTag(String dockerRepoImageTag) {
    this.dockerRepoImageTag = dockerRepoImageTag;
  }

  public String getDistributionPath() {
    return distributionPath;
  }

  public void setDistributionPath(String distributionPath) {
    this.distributionPath = distributionPath;
  }
}
