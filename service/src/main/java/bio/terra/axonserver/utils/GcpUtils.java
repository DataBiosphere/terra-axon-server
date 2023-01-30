package bio.terra.axonserver.utils;

import bio.terra.common.iam.SamUser;
import bio.terra.stairway.Step;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;

/** Utilities for interacting with Google Cloud APIs within {@link Step}s. */
public class GcpUtils {

  private GcpUtils() {}

  public static GoogleCredentials getGoogleCredentialsFromUserRequest(SamUser userRequest) {
    // The expirationTime argument is only used for refresh tokens, not access tokens.
    AccessToken accessToken = new AccessToken(userRequest.getBearerToken().getToken(), null);
    return GoogleCredentials.create(accessToken);
  }
}
