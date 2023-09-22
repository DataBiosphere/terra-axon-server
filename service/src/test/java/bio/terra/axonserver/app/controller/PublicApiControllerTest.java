package bio.terra.axonserver.app.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.axonserver.app.configuration.CliConfiguration;
import bio.terra.axonserver.app.configuration.VersionConfiguration;
import bio.terra.axonserver.testutils.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class PublicApiControllerTest extends BaseUnitTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private VersionConfiguration versionConfiguration;
  @Autowired private CliConfiguration cliConfiguration;

  @Test
  void testStatus() throws Exception {
    this.mockMvc.perform(get("/status")).andExpect(status().isOk());
  }

  @Test
  void testVersion() throws Exception {
    this.mockMvc
        .perform(get("/version"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.gitTag").value(versionConfiguration.getGitTag()))
        .andExpect(jsonPath("$.gitHash").value(versionConfiguration.getGitHash()))
        .andExpect(jsonPath("$.github").value(versionConfiguration.getGithub()))
        .andExpect(jsonPath("$.build").value(versionConfiguration.getBuild()))
        .andExpect(jsonPath("$.oldestSupportedCli").value(cliConfiguration.getOldestVersion()))
        .andExpect(jsonPath("$.latestSupportedCli").value(cliConfiguration.getLatestVersion()))
        .andExpect(jsonPath("$.cliDockerRepoHost").value(cliConfiguration.getDockerRepoHost()))
        .andExpect(
            jsonPath("$.cliDockerImageName").value(cliConfiguration.getDockerRepoImageName()))
        .andExpect(jsonPath("$.cliDockerImageTag").value(cliConfiguration.getDockerRepoImageTag()))
        .andExpect(jsonPath("$.cliDistributionPath").value(cliConfiguration.getDistributionPath()));
  }

  @Test
  void testGetSwagger() throws Exception {
    this.mockMvc.perform(get("/")).andExpect(status().isOk());
  }
}
