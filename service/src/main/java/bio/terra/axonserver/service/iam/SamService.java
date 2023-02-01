package bio.terra.axonserver.service.iam;

import bio.terra.axonserver.app.configuration.SamConfiguration;
import bio.terra.axonserver.utils.GcpUtils;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.sam.exception.SamExceptionFactory;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.GoogleApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SamService {

  private final SamConfiguration samConfig;

  @Autowired
  public SamService(SamConfiguration samConfig) {
    this.samConfig = samConfig;
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(accessToken);
    return apiClient;
  }

  private ApiClient getApiClient() {
    return new ApiClient().setBasePath(samConfig.basePath());
  }

  /**
   * Get a pet service account access token for a user.
   *
   * @param projectId google project id
   * @param userRequest user access token
   * @return pet service account access token
   */
  public String getPetAccessToken(String projectId, BearerToken userRequest) {
    try {
      return new GoogleApi(getApiClient(userRequest.getToken())).getPetServiceAccountToken(
          projectId, GcpUtils.getPetScopes());
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error getting user's pet SA access token", apiException);
    }
  }
}
