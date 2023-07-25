package bio.terra.axonserver.service.cloud.aws;

import bio.terra.axonserver.service.iam.SamService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.common.iam.BearerToken;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Service for interacting with GCP API surface. */
@Component
public class GcpService {
  // Google pet service account scopes for accessing Google Cloud APIs.
  private static final List<String> PET_SA_SCOPES =
      ImmutableList.of(
          "openid", "email", "profile", "https://www.googleapis.com/auth/cloud-platform");

  public static List<String> getPetScopes() {
    return PET_SA_SCOPES;
  }

  private final WorkspaceManagerService wsmService;
  private final SamService samService;

  public GcpService(WorkspaceManagerService wsmService, SamService samService) {
    this.wsmService = wsmService;
    this.samService = samService;
  }

  /**
   * Get GoogleCredentials from an access token
   *
   * @param token token to use for the credentials
   * @return GoogleCredentials
   */
  private static GoogleCredentials getGoogleCredentialsFromToken(String token) {
    // The expirationTime argument is only used for refresh tokens, not access tokens.
    AccessToken accessToken = new AccessToken(token, null);
    return GoogleCredentials.create(accessToken);
  }

  public GoogleCredentials getPetSACredentials(UUID workspaceId, BearerToken token) {
    String projectId = wsmService.getGcpContext(workspaceId, token.getToken()).getProjectId();
    String petAccessToken = samService.getPetAccessToken(projectId, token);
    return getGoogleCredentialsFromToken(petAccessToken);
  }
}
