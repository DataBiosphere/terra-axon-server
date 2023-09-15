package bio.terra.axonserver.service.iam;

import bio.terra.axonserver.app.configuration.SamConfiguration;
import bio.terra.axonserver.service.cloud.gcp.GcpService;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.sam.exception.SamExceptionFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Set;
import org.broadinstitute.dsde.workbench.client.sam.ApiClient;
import org.broadinstitute.dsde.workbench.client.sam.ApiException;
import org.broadinstitute.dsde.workbench.client.sam.api.GoogleApi;
import org.broadinstitute.dsde.workbench.client.sam.api.UsersApi;
import org.broadinstitute.dsde.workbench.client.sam.model.UserStatusInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SamService {

  private static final Set<String> SAM_OAUTH_SCOPES = ImmutableSet.of("openid", "email", "profile");
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
      return new GoogleApi(getApiClient(userRequest.getToken()))
          .getPetServiceAccountToken(projectId, GcpService.getPetScopes());
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error getting user's pet SA access token", apiException);
    }
  }

  /**
   * Get registration info for a user (email, enabled status)
   *
   * @param userRequest user access token
   * @return
   */
  public UserStatusInfo getUserStatusInfo(BearerToken userRequest) {
    try {
      return new UsersApi(getApiClient(userRequest.getToken())).getUserStatusInfo();
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error getting user's info", apiException);
    }
  }

  /**
   * Get the pet service account email for a user, within a specified project.
   *
   * @param projectId Google project ID
   * @param userRequest User access token
   * @return The pet service account email.
   */
  public String getPetServiceAccount(String projectId, BearerToken userRequest) {
    try {
      return new GoogleApi(getApiClient(userRequest.getToken())).getPetServiceAccount(projectId);
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create("Error getting user's pet SA email.", apiException);
    }
  }

  public String getPetServiceAccountKey(String projectId, String userEmail) {
    try {
      return new GoogleApi(getApiClient(getAxonServiceAccountToken()))
          .getUserPetServiceAccountKey(projectId, userEmail);
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create(
          "Error getting user's pet SA key for project.", apiException);
    }
  }

  public String getAxonServiceAccountToken() {
    try {
      GoogleCredentials creds =
          GoogleCredentials.getApplicationDefault().createScoped(SAM_OAUTH_SCOPES);
      creds.refreshIfExpired();
      return creds.getAccessToken().getTokenValue();
    } catch (IOException e) {
      throw new InternalServerErrorException(
          "Internal server error retrieving axon credentials", e);
    }
  }
}
