package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.PublicApi;
import bio.terra.axonserver.app.configuration.CliVersionConfiguration;
import bio.terra.axonserver.app.configuration.VersionConfiguration;
import bio.terra.axonserver.model.ApiVersionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class PublicApiController implements PublicApi {
  private final VersionConfiguration versionConfiguration;
  private final CliVersionConfiguration cliVersionConfiguration;

  @Autowired
  public PublicApiController(
      CliVersionConfiguration cliVersionConfiguration, VersionConfiguration versionConfiguration) {
    this.cliVersionConfiguration = cliVersionConfiguration;
    this.versionConfiguration = versionConfiguration;
  }

  @Override
  public ResponseEntity<Void> serviceStatus() {
    return new ResponseEntity<>(HttpStatus.OK);
  }

  @Override
  public ResponseEntity<ApiVersionProperties> serviceVersion() {
    ApiVersionProperties currentVersion =
        new ApiVersionProperties()
            .gitTag(versionConfiguration.getGitTag())
            .gitHash(versionConfiguration.getGitHash())
            .github(versionConfiguration.getGithub())
            .build(versionConfiguration.getBuild())
            .latestSupportedCli(cliVersionConfiguration.getLatestVersion())
            .oldestSupportedCli(cliVersionConfiguration.getOldestVersion());
    return new ResponseEntity<>(currentVersion, HttpStatus.OK);
  }
}
