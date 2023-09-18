package bio.terra.axonserver.service.iam;

import bio.terra.axonserver.app.configuration.SamConfiguration;
import bio.terra.axonserver.service.cloud.gcp.GcpService;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.sam.SamRetry;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SamService {
  private static final Logger logger = LoggerFactory.getLogger(SamService.class);
  private static final Set<String> SAM_OAUTH_SCOPES = ImmutableSet.of("openid", "email", "profile");
  private final SamConfiguration samConfig;
  private boolean axonServiceAccountInitialized;

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
      initializeAxonServiceAccount();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    try {
      return new GoogleApi(getApiClient(getAxonServiceAccountToken()))
          .getUserPetServiceAccountKey(projectId, userEmail);
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create(
          "Error getting user's pet SA key for project.", apiException);
    }
  }

  private void initializeAxonServiceAccount() throws InterruptedException {
    if (!axonServiceAccountInitialized) {
      final String axonAccessToken;
      try {
        axonAccessToken = getAxonServiceAccountToken();
      } catch (InternalServerErrorException e) {
        // In cases where Axon is not running as a service account (e.g. unit tests), the above call
        // will throw. This can be ignored now and later when the credentials are used again.
        logger.warn(
            "Failed to register Axon service account in Sam. This is expected for tests.", e);
        return;
      }
      UsersApi usersApi = new UsersApi(getApiClient(axonAccessToken));
      // If registering the service account fails, all we can do is to keep trying.
      if (!axonServiceAccountRegistered(usersApi)) {
        // retries internally
        registerAxonServiceAccount(usersApi);
      }
      axonServiceAccountInitialized = true;
    }
  }

  public boolean axonServiceAccountRegistered(UsersApi usersApi) throws InterruptedException {
    try {
      // getUserStatusInfo throws a 404 if the calling user is not registered, which will happen
      // the first time Axon is run in each environment.
      SamRetry.retry(usersApi::getUserStatusInfo);
      logger.info("Axon service account already registered in Sam");
      return true;
    } catch (ApiException apiException) {
      if (apiException.getCode() == HttpStatus.NOT_FOUND.value()) {
        logger.info(
            "Sam error was NOT_FOUND when checking user registration. This means the "
                + " user is not registered but is not an exception. Returning false.");
        return false;
      } else {
        throw SamExceptionFactory.create("Error checking user status in Sam", apiException);
      }
    }
  }

  private void registerAxonServiceAccount(UsersApi usersApi) throws InterruptedException {
    try {
      SamRetry.retry(() -> usersApi.createUserV2(""));
    } catch (ApiException apiException) {
      throw SamExceptionFactory.create(
          "Error registering Axon service account with Sam", apiException);
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
