package scripts.testscripts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bio.terra.axonserver.api.PublicApi;
import bio.terra.axonserver.client.ApiClient;
import bio.terra.testrunner.runner.TestScript;
import bio.terra.testrunner.runner.config.TestUserSpecification;
import com.google.api.client.http.HttpStatusCodes;
import scripts.utils.ClientTestUtils;

public class GetStatus extends TestScript {
  @Override
  public void userJourney(TestUserSpecification testUser) throws Exception {
    ApiClient apiClient = ClientTestUtils.getClientWithoutAccessToken(server);
    var publicApi = new PublicApi(apiClient);

    publicApi.serviceStatus();
    assertThat(apiClient.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));

    var versionProperties = publicApi.serviceVersion();
    assertThat(apiClient.getStatusCode(), is(HttpStatusCodes.STATUS_CODE_OK));

    assertNotNull(versionProperties.getGitHash());
    assertNotNull(versionProperties.getGitTag());
    assertNotNull(versionProperties.getGithub());
    assertNotNull(versionProperties.getBuild());
  }
}
